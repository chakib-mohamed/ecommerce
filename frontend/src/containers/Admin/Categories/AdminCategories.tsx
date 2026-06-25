import { useEffect, useState } from 'react';
import ConfirmDialog from '../../../components/UI/ConfirmDialog/ConfirmDialog';
import Button from '../../../components/UI/Button/Button';
import { type Category } from '../../../data/catalog';
import { useCatalogCategories } from '../../../lib/use-catalog';

interface Editing {
  type: 'cat' | 'sub';
  catId: string;
  subId: string | null;
  value: string;
}

interface Confirm {
  message: string;
  onConfirm: () => void;
}

const EDIT_INPUT =
  'border-[1.5px] border-accent rounded-md px-2 py-1 text-sm outline-none bg-paper w-[180px]';

/** Category accordion — rename, delete (with confirmation) and add sub­categories.
 *  Edits live in local state; persistence arrives with the real-data follow-up. */
export default function AdminCategories() {
  const seedCategories = useCatalogCategories();
  const [cats, setCats] = useState<Category[]>([]);
  const [open, setOpen] = useState<string | null>(null);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const [addingTo, setAddingTo] = useState<string | null>(null);
  const [newSub, setNewSub] = useState('');

  // Seed the editable copy from the loaded catalog (and re-seed if it changes).
  useEffect(() => {
    setCats(seedCategories.map((c) => ({ ...c, subs: [...c.subs] })));
    setOpen((cur) => cur ?? seedCategories[0]?.id ?? null);
  }, [seedCategories]);

  const startEdit = (type: 'cat' | 'sub', catId: string, subId: string | null, value: string) =>
    setEditing({ type, catId, subId, value });

  const commitEdit = () => {
    if (!editing || editing.value.trim() === '') return;
    setCats((prev) =>
      prev.map((c) => {
        if (c.id !== editing.catId) return c;
        if (editing.type === 'cat') return { ...c, name: editing.value };
        return { ...c, subs: c.subs.map((s) => (s.id === editing.subId ? { ...s, name: editing.value } : s)) };
      }),
    );
    setEditing(null);
  };

  const deleteCat = (catId: string) =>
    setConfirm({
      message: `Delete "${cats.find((c) => c.id === catId)?.name}" and all its subcategories? This cannot be undone.`,
      onConfirm: () => {
        setCats((prev) => prev.filter((c) => c.id !== catId));
        setConfirm(null);
      },
    });

  const deleteSub = (catId: string, subId: string) =>
    setConfirm({
      message: `Delete "${cats.find((c) => c.id === catId)?.subs.find((s) => s.id === subId)?.name}"? This cannot be undone.`,
      onConfirm: () => {
        setCats((prev) =>
          prev.map((c) => (c.id !== catId ? c : { ...c, subs: c.subs.filter((s) => s.id !== subId) })),
        );
        setConfirm(null);
      },
    });

  const commitNewSub = (catId: string) => {
    const name = newSub.trim();
    if (name === '') {
      setAddingTo(null);
      return;
    }
    const id = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || `sub-${Date.now()}`;
    setCats((prev) => prev.map((c) => (c.id !== catId ? c : { ...c, subs: [...c.subs, { id, name }] })));
    setNewSub('');
    setAddingTo(null);
  };

  const onEditKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') commitEdit();
    if (e.key === 'Escape') setEditing(null);
  };

  return (
    <div className="px-10 py-9 max-w-[760px] flex-1">
      <div className="mb-7 reveal">
        <span className="eyebrow">Organize</span>
        <h1 className="display text-4xl mt-1.5">Categories</h1>
      </div>

      <div className="grid gap-2.5">
        {cats.map((cat) => {
          const isOpen = open === cat.id;
          const editingThisCat = editing?.type === 'cat' && editing.catId === cat.id;
          return (
            <div key={cat.id} className="bg-surface border border-line rounded-md overflow-hidden">
              <div
                className={`flex items-center justify-between px-5 py-4 ${isOpen ? 'border-b border-line' : ''}`}
              >
                {editingThisCat ? (
                  <div className="flex items-center gap-2">
                    <input
                      autoFocus
                      className={EDIT_INPUT}
                      value={editing.value}
                      onChange={(e) => setEditing({ ...editing, value: e.target.value })}
                      onKeyDown={onEditKey}
                    />
                    <Button variant="primary" size="sm" onClick={commitEdit}>
                      Save
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setEditing(null)}>
                      Cancel
                    </Button>
                  </div>
                ) : (
                  <span className="font-serif text-[17px] font-semibold">{cat.name}</span>
                )}

                <div className="flex items-center gap-2.5">
                  {!editingThisCat && (
                    <div className="flex gap-1.5">
                      <Button variant="quiet" size="sm" onClick={() => startEdit('cat', cat.id, null, cat.name)}>
                        Edit
                      </Button>
                      <Button variant="quiet" size="sm" className="!text-accent" onClick={() => deleteCat(cat.id)}>
                        Delete
                      </Button>
                    </div>
                  )}
                  <span className="text-muted text-[13px]">{cat.subs.length} subs</span>
                  <button
                    onClick={() => setOpen(isOpen ? null : cat.id)}
                    className="border-0 bg-transparent cursor-pointer p-1 text-muted text-[11px] transition-transform duration-150"
                    style={{ transform: isOpen ? 'rotate(180deg)' : 'none' }}
                    aria-label={isOpen ? 'Collapse' : 'Expand'}
                  >
                    ▼
                  </button>
                </div>
              </div>

              {isOpen && (
                <div className="py-1 pb-2">
                  {cat.subs.map((s) => {
                    const editingThisSub =
                      editing?.type === 'sub' && editing.catId === cat.id && editing.subId === s.id;
                    return (
                      <div key={s.id} className="flex items-center justify-between px-5 pl-8 py-2.5 text-sm">
                        {editingThisSub ? (
                          <div className="flex items-center gap-2">
                            <input
                              autoFocus
                              className={EDIT_INPUT}
                              value={editing.value}
                              onChange={(e) => setEditing({ ...editing, value: e.target.value })}
                              onKeyDown={onEditKey}
                            />
                            <Button variant="primary" size="sm" onClick={commitEdit}>
                              Save
                            </Button>
                            <Button variant="ghost" size="sm" onClick={() => setEditing(null)}>
                              Cancel
                            </Button>
                          </div>
                        ) : (
                          <>
                            <span>{s.name}</span>
                            <div className="flex gap-1.5">
                              <Button
                                variant="quiet"
                                size="sm"
                                onClick={() => startEdit('sub', cat.id, s.id, s.name)}
                              >
                                Edit
                              </Button>
                              <Button
                                variant="quiet"
                                size="sm"
                                className="!text-accent"
                                onClick={() => deleteSub(cat.id, s.id)}
                              >
                                Delete
                              </Button>
                            </div>
                          </>
                        )}
                      </div>
                    );
                  })}

                  <div className="px-5 pl-8 pt-2 pb-1">
                    {addingTo === cat.id ? (
                      <div className="flex items-center gap-2">
                        <input
                          autoFocus
                          className={EDIT_INPUT}
                          placeholder="Subcategory name"
                          value={newSub}
                          onChange={(e) => setNewSub(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') commitNewSub(cat.id);
                            if (e.key === 'Escape') {
                              setNewSub('');
                              setAddingTo(null);
                            }
                          }}
                        />
                        <Button variant="primary" size="sm" onClick={() => commitNewSub(cat.id)}>
                          Add
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setNewSub('');
                            setAddingTo(null);
                          }}
                        >
                          Cancel
                        </Button>
                      </div>
                    ) : (
                      <Button variant="quiet" size="sm" onClick={() => setAddingTo(cat.id)}>
                        + Add subcategory
                      </Button>
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {confirm && (
        <ConfirmDialog message={confirm.message} onConfirm={confirm.onConfirm} onCancel={() => setConfirm(null)} />
      )}
    </div>
  );
}
