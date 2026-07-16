import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, X, Search, Package, Users, ShoppingCart, Trash2, CheckCircle, AlertTriangle } from 'lucide-react'
import clsx from 'clsx'
import { useToast } from '../hooks/useToast'
import { createOrder, fetchCustomers, fetchProducts } from '../api/newBackend'
import Autocomplete from '../components/common/Autocomplete'
import PermissionGate from '../components/rbac/PermissionGate'

interface LineItem {
  id: string
  sku: string
  productName: string
  qty: number
  price: number
}

export default function CreateOrderPage() {
  const { addToast } = useToast()
  const navigate = useNavigate()
  const [step, setStep] = useState<'customer' | 'items' | 'shipping' | 'review'>('customer')
  const [customerSearch, setCustomerSearch] = useState('')
  const [selectedCustomer, setSelectedCustomer] = useState<any>(null)
  const [lineItems, setLineItems] = useState<LineItem[]>([])
  const [productSearch, setProductSearch] = useState('')
  const [shippingInfo, setShippingInfo] = useState({ address: '', city: '', state: '', zip: '', country: 'US', method: 'ground', notes: '' })
  const [orderSource, setOrderSource] = useState('manual')
  const [createAnother, setCreateAnother] = useState(false)
  const [customerList, setCustomerList] = useState<any[]>([])
  const [productList, setProductList] = useState<any[]>([])

  useEffect(() => {
    Promise.all([fetchCustomers(), fetchProducts()]).then(([c, p]) => {
      if (c?.customers) setCustomerList(c.customers)
      if (p?.products) setProductList(p.products)
    }).catch((err) => {
      addToast({ type: 'error', title: 'Failed to load data', description: err?.message })
    })
  }, [])

  const filteredProducts = productList.filter((p: any) =>
    !productSearch.trim() || p.name.toLowerCase().includes(productSearch.toLowerCase()) || p.sku?.toLowerCase().includes(productSearch.toLowerCase())
  )

  const addItem = (product: any) => {
    setLineItems(prev => {
      const existing = prev.find(i => i.sku === product.sku)
      if (existing) return prev.map(i => i.sku === product.sku ? { ...i, qty: i.qty + 1 } : i)
      return [...prev, { id: `li-${Date.now()}`, sku: product.sku, productName: product.name, qty: 1, price: product.price }]
    })
  }

  const updateQty = (id: string, qty: number) => {
    if (qty <= 0) { setLineItems(prev => prev.filter(i => i.id !== id)); return }
    setLineItems(prev => prev.map(i => i.id === id ? { ...i, qty } : i))
  }

  const removeItem = (id: string) => setLineItems(prev => prev.filter(i => i.id !== id))

  const subtotal = lineItems.reduce((s, i) => s + i.price * i.qty, 0)
  const tax = subtotal * 0.08
  const shipping = shippingInfo.method === 'express' ? 24.99 : shippingInfo.method === 'overnight' ? 49.99 : 9.99
  const total = subtotal + tax + shipping

  async function handleSubmitOrder() {
    const orderData = {
      customerId: selectedCustomer?.id || 'C001',
      customerName: selectedCustomer?.name || '',
      customerEmail: selectedCustomer?.email || '',
      items: lineItems,
      subtotal,
      shipping,
      tax,
      total,
      shippingMethod: shippingInfo.method,
      shippingAddress: shippingInfo.address,
      notes: shippingInfo.notes,
    }
    try {
      const res = await createOrder(orderData)
      if (res?.order) {
        addToast({ type: 'success', title: `Order ${res.order.orderNumber} created` })
        if (!createAnother) navigate('/orders')
        else {
          setSelectedCustomer(null)
          setLineItems([])
          setShippingInfo({ address: '', city: '', state: '', zip: '', country: 'US', method: 'ground', notes: '' })
          setStep('customer')
        }
      } else {
        addToast({ type: 'error', title: 'Failed to create order', description: res?.error || 'Unknown error' })
      }
    } catch (err: any) {
      addToast({ type: 'error', title: 'Failed to create order', description: err?.message })
    }
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="enterprise-page-header">
        <div>
          <h1 className="flex items-center gap-2.5"><Plus className="w-7 h-7 text-primary-500" /> Create Order</h1>
          <p>Manual order entry</p>
        </div>
      </div>

      {/* Steps */}
      <div className="flex items-center gap-2">
        {['customer', 'items', 'shipping', 'review'].map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <button onClick={() => setStep(s as any)} className={clsx('w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-colors', step === s ? 'bg-primary-600 text-white' : (['customer', 'items', 'shipping', 'review'].indexOf(step) > i ? 'bg-green-500 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-500'))}>
              {['customer', 'items', 'shipping', 'review'].indexOf(step) > i ? <CheckCircle className="w-4 h-4" /> : i + 1}
            </button>
            <span className={clsx('text-xs font-medium capitalize', step === s ? 'text-primary-600' : 'text-gray-400')}>{s}</span>
            {i < 3 && <div className={clsx('w-8 h-0.5', ['customer', 'items', 'shipping', 'review'].indexOf(step) > i ? 'bg-green-500' : 'bg-gray-200 dark:bg-gray-700')} />}
          </div>
        ))}
      </div>

      {step === 'customer' && (
        <div className="enterprise-card p-6">
          <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2"><Users className="w-5 h-5 text-primary-500" /> Select Customer</h2>
          <Autocomplete
            value={customerSearch}
            onChange={setCustomerSearch}
            onSelect={(c: any) => { setSelectedCustomer(c); setStep('items') }}
            suggestions={customerList}
            getOptionLabel={(c: any) => `${c.name} — ${c.email || c.id}`}
            getOptionValue={(c: any) => c.id}
            placeholder="Search customers by name, email or ID..."
            minChars={0}
            className="mb-4"
          />
          <div className="mt-4 p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50">
            <p className="text-xs text-[var(--text-tertiary)] mb-2">Or create as guest</p>
            <button onClick={() => { setSelectedCustomer({ id: 'guest', name: 'Guest Customer', email: '' }); setStep('items') }} className="text-sm text-primary-600 hover:text-primary-700 font-medium">Continue as Guest →</button>
          </div>
        </div>
      )}

      {step === 'items' && (
        <div className="enterprise-card p-6">
          <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2"><Package className="w-5 h-5 text-primary-500" /> Add Items</h2>
          <Autocomplete
            value={productSearch}
            onChange={setProductSearch}
            onSelect={(p: any) => addItem(p)}
            suggestions={productList}
            getOptionLabel={(p: any) => `${p.name} — ${p.sku} ($${p.price})`}
            getOptionValue={(p: any) => p.sku}
            placeholder="Search products by name or SKU..."
            minChars={0}
            className="mb-4"
          />
          {lineItems.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-2">Selected Items ({lineItems.length})</h3>
              <div className="space-y-2">
                {lineItems.map(item => (
                  <div key={item.id} className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 dark:bg-gray-800/50 border border-gray-100 dark:border-gray-700">
                    <div className="flex-1 min-w-0"><p className="text-sm font-medium text-[var(--text-primary)]">{item.productName}</p><p className="text-xs text-[var(--text-tertiary)]">{item.sku} — ${item.price.toFixed(2)} each</p></div>
                    <div className="flex items-center gap-2">
                      <button onClick={() => updateQty(item.id, item.qty - 1)} className="w-7 h-7 rounded-lg bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-sm font-medium hover:bg-gray-300 dark:hover:bg-gray-600">−</button>
                      <span className="w-8 text-center text-sm font-semibold text-[var(--text-primary)]">{item.qty}</span>
                      <button onClick={() => updateQty(item.id, item.qty + 1)} className="w-7 h-7 rounded-lg bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-sm font-medium hover:bg-gray-300 dark:hover:bg-gray-600">+</button>
                    </div>
                    <span className="text-sm font-semibold text-[var(--text-primary)] w-20 text-right">${(item.price * item.qty).toFixed(2)}</span>
                    <button onClick={() => removeItem(item.id)} className="p-1.5 rounded-lg hover:bg-red-50 text-gray-400 hover:text-red-500"><Trash2 className="w-4 h-4" /></button>
                  </div>
                ))}
              </div>
              <div className="flex justify-end mt-3 text-sm font-semibold text-[var(--text-primary)]">Subtotal: ${subtotal.toFixed(2)}</div>
            </div>
          )}
          <div className="flex justify-end mt-4">
            <button onClick={() => setStep('shipping')} disabled={lineItems.length === 0} className="enterprise-btn-primary disabled:opacity-50">Continue to Shipping →</button>
          </div>
        </div>
      )}

      {step === 'shipping' && (
        <div className="enterprise-card p-6">
          <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4 flex items-center gap-2"><ShoppingCart className="w-5 h-5 text-primary-500" /> Shipping Details</h2>
          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2"><label className="enterprise-label">Address</label><input value={shippingInfo.address} onChange={e => setShippingInfo(f => ({ ...f, address: e.target.value }))} className="enterprise-input w-full" placeholder="123 Main Street" /></div>
            <div><label className="enterprise-label">City</label><input value={shippingInfo.city} onChange={e => setShippingInfo(f => ({ ...f, city: e.target.value }))} className="enterprise-input w-full" placeholder="New York" /></div>
            <div><label className="enterprise-label">State</label><input value={shippingInfo.state} onChange={e => setShippingInfo(f => ({ ...f, state: e.target.value }))} className="enterprise-input w-full" placeholder="NY" /></div>
            <div><label className="enterprise-label">ZIP Code</label><input value={shippingInfo.zip} onChange={e => setShippingInfo(f => ({ ...f, zip: e.target.value }))} className="enterprise-input w-full" placeholder="10001" /></div>
            <div><label className="enterprise-label">Country</label><select value={shippingInfo.country} onChange={e => setShippingInfo(f => ({ ...f, country: e.target.value }))} className="enterprise-input w-full"><option value="US">United States</option><option value="CA">Canada</option><option value="UK">United Kingdom</option></select></div>
            <div className="col-span-2">
              <label className="enterprise-label">Shipping Method</label>
              <div className="flex gap-3">
                {[{ id: 'ground', label: 'Ground', price: '$9.99', eta: '5-7 days' }, { id: 'express', label: 'Express', price: '$24.99', eta: '2-3 days' }, { id: 'overnight', label: 'Overnight', price: '$49.99', eta: '1 day' }].map(m => (
                  <button key={m.id} onClick={() => setShippingInfo(f => ({ ...f, method: m.id }))} className={clsx('flex-1 p-3 rounded-lg border text-center transition-colors', shippingInfo.method === m.id ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-gray-200 dark:border-gray-700 hover:border-gray-300')}>
                    <p className="text-sm font-semibold text-[var(--text-primary)]">{m.label}</p>
                    <p className="text-xs text-[var(--text-tertiary)]">{m.price} · {m.eta}</p>
                  </button>
                ))}
              </div>
            </div>
            <div className="col-span-2"><label className="enterprise-label">Order Notes</label><textarea value={shippingInfo.notes} onChange={e => setShippingInfo(f => ({ ...f, notes: e.target.value }))} className="enterprise-input w-full" rows={2} placeholder="Optional notes for this order..." /></div>
          </div>
          <div className="flex justify-between mt-4">
            <button onClick={() => setStep('items')} className="enterprise-btn-secondary">← Back to Items</button>
            <button onClick={() => setStep('review')} className="enterprise-btn-primary">Review Order →</button>
          </div>
        </div>
      )}

      {step === 'review' && (
        <div className="space-y-4">
          <div className="enterprise-card p-6">
            <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-4">Order Summary</h2>
            <div className="space-y-4">
              <div><p className="text-xs font-medium text-[var(--text-tertiary)] uppercase">Customer</p><p className="text-sm font-medium text-[var(--text-primary)]">{selectedCustomer?.name}</p></div>
              <div><p className="text-xs font-medium text-[var(--text-tertiary)] uppercase">Items ({lineItems.length})</p>{lineItems.map(item => <div key={item.id} className="flex justify-between text-sm py-1"><span>{item.productName} × {item.qty}</span><span>${(item.price * item.qty).toFixed(2)}</span></div>)}</div>
              <div className="border-t border-gray-200 dark:border-gray-700 pt-3 space-y-1">
                <div className="flex justify-between text-sm"><span>Subtotal</span><span>${subtotal.toFixed(2)}</span></div>
                <div className="flex justify-between text-sm"><span>Tax (8%)</span><span>${tax.toFixed(2)}</span></div>
                <div className="flex justify-between text-sm"><span>Shipping ({shippingInfo.method})</span><span>${shipping.toFixed(2)}</span></div>
                <div className="flex justify-between text-lg font-bold text-[var(--text-primary)] border-t border-gray-200 dark:border-gray-700 pt-2"><span>Total</span><span>${total.toFixed(2)}</span></div>
              </div>
              <div><p className="text-xs font-medium text-[var(--text-tertiary)] uppercase">Ship To</p><p className="text-sm text-[var(--text-primary)]">{shippingInfo.address}, {shippingInfo.city}, {shippingInfo.state} {shippingInfo.zip}</p></div>
              <div><p className="text-xs font-medium text-[var(--text-tertiary)] uppercase">Notes</p><p className="text-sm text-[var(--text-secondary)]">{shippingInfo.notes || 'None'}</p></div>
            </div>
            <div className="flex items-center gap-2 mt-4">
              <input type="checkbox" id="createAnother" checked={createAnother} onChange={e => setCreateAnother(e.target.checked)} className="rounded border-gray-300" />
              <label htmlFor="createAnother" className="text-sm text-[var(--text-secondary)]">Create another order after submission</label>
            </div>
            <div className="flex justify-between mt-4">
              <button onClick={() => setStep('shipping')} className="enterprise-btn-secondary">← Back</button>
              <PermissionGate resource="orders" action="create">
                <button onClick={handleSubmitOrder} className="enterprise-btn-primary bg-green-600 hover:bg-green-700"><CheckCircle className="w-4 h-4" /> Submit Order</button>
              </PermissionGate>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
