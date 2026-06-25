import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import Button from '../../../components/UI/Button/Button';
import Field, { Input, Select } from '../../../components/UI/Field/Field';
import { useCatalogCategories, useCatalogProducts } from '../../../lib/use-catalog';

interface FormState {
  name: string;
  price: number;
  stock: number;
  cat: string;
  sub: string;
}

const blank: FormState = { name: '', price: 0, stock: 0, cat: '', sub: '' };

/** Create / edit a product. Validates client-side; persistence is wired in the
 *  real-data follow-up, so a save here confirms and returns to the list. */
export default function AdminProductForm() {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const products = useCatalogProducts();
  const categories = useCatalogCategories();
  const existing = id ? products.find((p) => p.id === id) : undefined;
  const isEdit = Boolean(id);

  const [form, setForm] = useState<FormState>(
    existing
      ? { name: existing.name, price: existing.price, stock: existing.stock, cat: existing.cat, sub: existing.sub }
      : blank,
  );

  // On an edit route the catalog may still be loading at mount — sync the form
  // once the product becomes available.
  useEffect(() => {
    if (existing) {
      setForm({
        name: existing.name,
        price: existing.price,
        stock: existing.stock,
        cat: existing.cat,
        sub: existing.sub,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existing?.id]);

  const set =
    (k: keyof FormState) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
      const v = k === 'price' || k === 'stock' ? Number(e.target.value) : e.target.value;
      setForm((f) => (k === 'cat' ? { ...f, cat: v as string, sub: '' } : { ...f, [k]: v }));
    };

  const cat = categories.find((c) => c.id === form.cat);
  const subRequired = (cat?.subs.length ?? 0) > 0;
  const ready =
    form.name.trim() !== '' && form.price > 0 && form.cat !== '' && (!subRequired || form.sub !== '');

  const save = () => {
    toast.success(`${isEdit ? 'Updated' : 'Created'} “${form.name}”`);
    navigate('/admin/products');
  };

  return (
    <div className="px-10 py-9 max-w-[640px] flex-1">
      <div className="flex items-start justify-between mb-8 reveal">
        <div>
          <span className="eyebrow">{isEdit ? 'Edit' : 'New'}</span>
          <h1 className="display text-4xl mt-1.5">{isEdit ? 'Edit product' : 'New product'}</h1>
        </div>
        <div className="flex gap-2.5">
          <Button variant="ghost" onClick={() => navigate('/admin/products')}>
            Cancel
          </Button>
          <Button variant="primary" disabled={!ready} onClick={save}>
            Save product
          </Button>
        </div>
      </div>

      <div className="bg-surface border border-line rounded-md p-7 reveal" style={{ animationDelay: '60ms' }}>
        <div className="grid gap-5">
          <Field label="Product name" hint="Required">
            <Input value={form.name} onChange={set('name')} placeholder="e.g. Linen Table Lamp" />
          </Field>

          <div className="grid grid-cols-2 gap-4">
            <Field label="Price ($)" hint="Required, greater than 0">
              <Input type="number" min={0} value={form.price} onChange={set('price')} />
            </Field>
            <Field label="Stock">
              <Input type="number" min={0} value={form.stock} onChange={set('stock')} />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Field label="Category" hint="Required">
              <Select value={form.cat} onChange={set('cat')}>
                <option value="">Choose…</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </Select>
            </Field>
            {subRequired && (
              <Field label="Subcategory" hint="Required">
                <Select value={form.sub} onChange={set('sub')} disabled={!cat}>
                  <option value="">Choose…</option>
                  {cat?.subs.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </Select>
              </Field>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
