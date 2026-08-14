import React, { useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import Badge, { BadgeTone } from "../../components/UI/Badge/Badge";
import Button from "../../components/UI/Button/Button";
import ConfirmDialog from "../../components/UI/ConfirmDialog/ConfirmDialog";
import Icon from "../../components/UI/Icon/Icon";
import Guard from "../../hoc/Guard/Guard";
import { service, User } from "../../services";
import type { OrderStatus, OrderSummary } from "../../services/rest-api-service";
import type { RootState } from "../../store";

const WRAP = "max-w-[860px] mx-auto px-6 pt-10 pb-20";
const PAGE_SIZE = 5;

const fmtMoney = (v: number) => "$" + v.toFixed(2);
const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "2-digit",
    year: "numeric",
  });

/**
 * How each lifecycle state is shown to the buyer.
 *
 * An order moves on its own once confirmed, so the states are not a binary: treating anything
 * that is not CONFIRMED as unfinished labelled a fully paid order "Pending". The wording is the
 * buyer's, not the system's - RESERVED is an internal step and reads as "Processing" here.
 */
const STATUS_BADGE: Record<OrderStatus, { label: string; tone: BadgeTone }> = {
  INITIATED: { label: "Pending", tone: "warn" },
  CONFIRMED: { label: "Processing", tone: "warn" },
  RESERVED: { label: "Processing", tone: "warn" },
  PAID: { label: "Paid", tone: "ok" },
  SHIPPED: { label: "Shipped", tone: "ok" },
  DELIVERED: { label: "Delivered", tone: "ok" },
  CANCELLED: { label: "Cancelled", tone: "neutral" },
  REFUNDED: { label: "Refunded", tone: "neutral" },
};

/** A state this build has not heard of: show it rather than rendering a blank pill. */
const UNKNOWN_STATUS = { label: "Unknown", tone: "neutral" as BadgeTone };

interface OrderCardProps {
  order: OrderSummary;
  delay: number;
  onCancel: () => void;
}
function OrderCard({ order, delay, onCancel }: OrderCardProps) {
  const count = order.products.reduce((n, p) => n + p.qty, 0);
  const { label, tone } = STATUS_BADGE[order.status] ?? UNKNOWN_STATUS;
  return (
    <div
      className="rounded-md bg-surface border border-line p-5 reveal"
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="flex items-start justify-between gap-4 mb-3">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="font-serif text-[18px]">Order #{order.id.slice(-6)}</span>
            <Badge tone={tone}>{label}</Badge>
          </div>
          <div className="text-muted text-[13px] mt-1">
            {fmtDate(order.creation_date)} · {count} item{count === 1 ? "" : "s"}
          </div>
          {/* Only ever set when an order ended somewhere the buyer did not choose, and it is the
              one thing they need: an order that vanished with no reason reads as money lost. */}
          {order.status_reason && (
            <div className="text-muted text-[13px] mt-1 italic">{order.status_reason}</div>
          )}
        </div>
        <span className="price font-serif text-[20px]">{fmtMoney(order.price)}</span>
      </div>

      <div className="flex flex-col gap-1.5 border-t border-line pt-3">
        {order.products.map((p) => (
          <div key={p.product_id} className="flex items-center justify-between text-sm">
            <span className="text-ink-2">
              <span className="text-muted">×{p.qty}</span> {p.title}
            </span>
            <span className="price text-ink-2">{fmtMoney(p.price * p.qty)}</span>
          </div>
        ))}
      </div>

      {/*
        This removes the order outright, which is only allowed while it is still INITIATED - an
        order the buyer has committed is no longer theirs to delete. The old test was "not
        CONFIRMED", which under two states meant the same thing and under eight offers the button
        on a paid order, where it can only fail.

        Cancelling a committed order is a different operation with its own endpoint, and it is not
        wired here: stock has been reserved by then and possibly charged, so it is not a delete.
      */}
      {order.status === "INITIATED" && (
        <div className="flex justify-end mt-3.5">
          <Button variant="ghost" size="sm" onClick={onCancel}>
            <Icon name="close" size={15} /> Cancel order
          </Button>
        </div>
      )}
    </div>
  );
}

interface PagerProps {
  page: number;
  pageCount: number;
  disabled?: boolean;
  onGo: (page: number) => void;
}
function Pager({ page, pageCount, disabled, onGo }: PagerProps) {
  return (
    <div className="flex items-center justify-center gap-2 mt-8">
      <Button variant="ghost" size="sm" disabled={disabled || page <= 1} onClick={() => onGo(page - 1)}>
        <Icon name="back" size={16} /> Prev
      </Button>
      <span className="text-muted text-sm px-2">
        Page {page} of {pageCount}
      </span>
      <Button
        variant="ghost"
        size="sm"
        disabled={disabled || page >= pageCount}
        onClick={() => onGo(page + 1)}
      >
        Next <Icon name="arrow" size={16} />
      </Button>
    </div>
  );
}

/** Order history — the buyer's past orders, with cancel for those still pending. */
const Orders: React.FC = () => {
  const navigate = useNavigate();
  const user = useSelector((state: RootState) => state.login.user);
  // Orders are owned by the signed-in buyer's account identity (their email), which is what
  // the order search filters on — not the opaque account id.
  const buyerId = user && user !== "anonymous" ? (user as User).email : undefined;

  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [cancelId, setCancelId] = useState<string | null>(null);

  const load = useCallback(
    (targetPage: number) => {
      if (!buyerId) return;
      setLoading(true);
      service
        .fetchOrders(buyerId, targetPage, PAGE_SIZE)
        .then(({ x, y }) => {
          setTotal(x);
          setOrders(y ?? []);
          setPage(targetPage);
        })
        .catch(() => {
          // The API client surfaces failures via a toast.
        })
        .finally(() => setLoading(false));
    },
    [buyerId]
  );

  useEffect(() => {
    load(1);
  }, [load]);

  const confirmCancel = () => {
    if (!cancelId) return;
    service
      .deleteOrder(cancelId)
      .then(() => {
        // Step back a page if we just cancelled the only order on this one.
        const emptied = orders.length === 1 && page > 1;
        setCancelId(null);
        load(emptied ? page - 1 : page);
      })
      .catch(() => setCancelId(null));
  };

  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  if (!buyerId) {
    return (
      <div className={`${WRAP} text-center`}>
        <h1 className="display text-[40px] mb-3">Your orders</h1>
        <p className="text-muted mb-6">Log in to see your order history.</p>
        <Button
          variant="primary"
          size="lg"
          onClick={() => navigate("/login", { state: { from: "/orders" } })}
        >
          Log in
        </Button>
      </div>
    );
  }

  return (
    <div className={WRAP}>
      <Button variant="quiet" size="sm" onClick={() => navigate("/account")}>
        <Icon name="back" size={16} /> Account
      </Button>
      <div className="flex items-end justify-between gap-4 mt-3 mb-7">
        <div>
          <span className="eyebrow reveal">History</span>
          <h1 className="display text-[46px] mt-2.5 reveal" style={{ animationDelay: "60ms" }}>
            Your orders
          </h1>
        </div>
        {total > 0 && (
          <span className="text-muted text-sm reveal">
            {total} order{total === 1 ? "" : "s"}
          </span>
        )}
      </div>

      {loading && orders.length === 0 ? (
        <p className="text-muted text-center py-16">Loading your orders…</p>
      ) : orders.length === 0 ? (
        <div className="rounded-md bg-surface border border-line p-12 text-center reveal">
          <div
            className="w-14 h-14 rounded-full grid place-items-center mx-auto mb-4"
            style={{ background: "var(--paper-2)", color: "var(--muted)" }}
          >
            <Icon name="cart" size={26} />
          </div>
          <h2 className="font-serif text-[24px] mb-2">No orders yet</h2>
          <p className="text-muted mb-5">
            You haven't placed any orders. Start exploring the shop.
          </p>
          <Button variant="primary" onClick={() => navigate("/browse")}>
            Start shopping
          </Button>
        </div>
      ) : (
        <>
          <div className="flex flex-col gap-3.5">
            {orders.map((order, i) => (
              <OrderCard
                key={order.id}
                order={order}
                delay={i * 40}
                onCancel={() => setCancelId(order.id)}
              />
            ))}
          </div>
          {pageCount > 1 && (
            <Pager page={page} pageCount={pageCount} disabled={loading} onGo={load} />
          )}
        </>
      )}

      {cancelId && (
        <ConfirmDialog
          title="Cancel this order?"
          message="This will cancel your order. This action can't be undone."
          confirmLabel="Cancel order"
          cancelLabel="Keep order"
          onConfirm={confirmCancel}
          onCancel={() => setCancelId(null)}
        />
      )}
    </div>
  );
};

const OrdersPage = Guard(Orders);
export default OrdersPage;
