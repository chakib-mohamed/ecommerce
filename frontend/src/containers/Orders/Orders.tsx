import { format } from "date-fns";
import React, { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import Paginator from "../../components/UI/Paginator/Paginator";
import Spinner from "../../components/UI/Spinner/Spinner";
import Guard from "../../hoc/Guard/Guard";
import Modal from "../../hoc/Modal/Modal";
import { AppDispatch, RootState } from "../../store";
import * as actions from "../../store/Orders/actions";
import { User } from "../../services";

type OrderProduct = { productID: string; qty: number; title: string };
type Order = {
  id: string;
  products: OrderProduct[];
  creationDate: string;
  price: number;
  status: string;
};

const Orders: React.FC = () => {
  const pageSize = 5;

  const { orders, pagesCount, orderID, loading, displayDeleteModalConfirmation } = useSelector((state: RootState) => state.orders);
  const { user } = useSelector((state: RootState) => state.login);

  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    if (user && user !== "anonymous" && (user as User).uid) {
      dispatch(actions.fetchOrders((user as User).uid, 1, pageSize));
    }
    return () => {
      dispatch(actions.resetState());
    };
  }, [user, pageSize, dispatch]);

  const reloadOrders = (currentPage: number) => {
    dispatch(actions.fetchOrders((user as User).uid, currentPage, pageSize));
  };

  const getStatusStyle = (status: string) => {
    switch (status) {
      case "INITIATED":
        return "bg-blue-50 text-blue-600 border-blue-100";
      case "COMPLETED":
        return "bg-green-50 text-green-600 border-green-100";
      case "CANCELLED":
        return "bg-red-50 text-red-600 border-red-100";
      default:
        return "bg-slate-50 text-slate-600 border-slate-100";
    }
  };

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="flex flex-col sm:flex-row justify-between items-center mb-10 space-y-4 sm:space-y-0 text-center sm:text-left">
        <div className="space-y-2">
          <h1 className="text-4xl font-black text-slate-900 tracking-tight">Order History</h1>
          <p className="text-slate-500 font-medium italic">Manage and track your recent purchases</p>
        </div>
        <div className="inline-flex items-center space-x-2 bg-blue-50 px-4 py-2 rounded-2xl border border-blue-100 shadow-sm">
          <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse"></div>
          <span className="text-sm font-bold text-blue-700">{orders?.length || 0} Orders total</span>
        </div>
      </div>

      <div className="relative">
        <Spinner loading={loading} />

        {orders && orders.length > 0 ? (
          <div className="space-y-8 animate-in fade-in slide-in-from-bottom duration-700">
            <div className="overflow-hidden bg-white/50 backdrop-blur-xl rounded-[2.5rem] border border-white/40 shadow-2xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50/50 border-b border-slate-100">
                      <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic">Items</th>
                      <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic">Date</th>
                      <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic">Total</th>
                      <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic">Status</th>
                      <th className="px-8 py-6 text-sm font-black text-slate-900 uppercase tracking-widest italic text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {(orders as unknown as Order[]).map((order) => (
                      <tr key={order.id} className="group hover:bg-blue-50/30 transition-colors duration-300">
                        <td className="px-8 py-8">
                          <div className="space-y-2">
                            {order.products.map((p: OrderProduct) => (
                              <div key={p.productID} className="flex items-center space-x-3">
                                <span className="bg-white border border-slate-100 text-[10px] font-black px-2 py-0.5 rounded-lg text-blue-600 shadow-sm">
                                  x{p.qty}
                                </span>
                                <span className="text-sm font-bold text-slate-900 group-hover:text-blue-700 transition-colors">
                                  {p.title}
                                </span>
                              </div>
                            ))}
                          </div>
                        </td>
                        <td className="px-8 py-8">
                          <div className="flex flex-col">
                            <span className="text-sm font-black text-slate-900 tracking-tight">
                              {format(new Date(order.creationDate), "MMM dd, yyyy")}
                            </span>
                            <span className="text-xs font-medium text-slate-400 italic">
                              {format(new Date(order.creationDate), "HH:mm")}
                            </span>
                          </div>
                        </td>
                        <td className="px-8 py-8">
                          <span className="text-lg font-black text-slate-900 italic tracking-tighter">
                            {order.price.toFixed(2)} $
                          </span>
                        </td>
                        <td className="px-8 py-8">
                          <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-black border tracking-wide uppercase shadow-sm ${getStatusStyle(order.status)}`}>
                            {order.status}
                          </span>
                        </td>
                        <td className="px-8 py-8 text-right">
                          {order.status === "INITIATED" && (
                            <button
                              onClick={() => dispatch(actions.openDeleteOrderModal(order.id))}
                              className="p-3 bg-white text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-2xl border border-slate-100 hover:border-red-100 shadow-sm transition-all active:scale-95 cursor-pointer"
                              title="Cancel Order"
                            >
                              <i className="fa fa-trash text-sm"></i>
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="flex justify-center pt-8">
              <Paginator
                pagesCount={pagesCount ?? 0}
                onPaginate={reloadOrders}
              />
            </div>
          </div>
        ) : !loading ? (
          <div className="bg-white p-20 rounded-[3rem] border border-slate-100 shadow-xl text-center space-y-6">
            <div className="inline-flex items-center justify-center p-8 bg-slate-50 rounded-full mb-4">
              <i className="fa fa-receipt text-6xl text-slate-300"></i>
            </div>
            <h2 className="text-3xl font-black text-slate-900 tracking-tight">No orders found</h2>
            <p className="text-slate-500 max-w-sm mx-auto font-medium">
              You haven't placed any orders yet. Start shopping to fill your history!
            </p>
          </div>
        ) : null}
      </div>

      {displayDeleteModalConfirmation && (
        <Modal
          displayModal={displayDeleteModalConfirmation}
          closeModalHandler={() => dispatch(actions.closeDeleteOrderModal())}
          submitHandler={() => dispatch(actions.deleteOrder(orderID as string, (user as User).uid))}
          className="w-full max-w-md"
          title="Cancel Order"
        >
          <div className="p-8 text-center space-y-6">
            <div className="inline-flex items-center justify-center p-5 bg-red-50 rounded-full text-red-500 mb-2">
              <i className="fa fa-exclamation-triangle text-3xl"></i>
            </div>
            <div className="space-y-2">
              <h3 className="text-xl font-black text-slate-900 tracking-tight">Confirm Cancellation</h3>
              <p className="text-slate-500 font-medium">
                Are you sure you want to cancel this order? This action cannot be undone.
              </p>
            </div>
          </div>
        </Modal>
      )}
    </main>
  );
};

const OrdersPage = Guard(Orders);
export default OrdersPage;
