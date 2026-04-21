import { useState, useEffect, useRef } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import type { Item, Shop } from '@/types'
import { useAuth } from '@/context/AuthContext'
import { api } from '@/lib/api'
import { Card, Button, FormField, TextInput, StatusBanner, EmptyState, Spinner } from '@/components/ui'
import { PageContainer } from '@/components/layout'
import { ChevronLeftIcon } from '@/components/icons'
import { formatCurrency } from '@/lib/utils'

const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const

export default function CreateTemplatePage() {
  const [searchParams] = useSearchParams()
  const shopId = searchParams.get('shopId') ?? ''
  const { user, token, isSeller } = useAuth()
  const navigate = useNavigate()

  const [items, setItems] = useState<Item[]>([])
  const [shop, setShop] = useState<Shop | null>(null)
  const [loadingItems, setLoadingItems] = useState(true)
  const [itemId, setItemId] = useState('')
  const [duration, setDuration] = useState('5')
  const [startBid, setStartBid] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [minIncrement, setMinIncrement] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [pickupOffset, setPickupOffset] = useState('30')
  const [pickupWindow, setPickupWindow] = useState('60')
  const [scheduleType, setScheduleType] = useState<'daily' | 'weekly'>('daily')
  const [scheduleDays, setScheduleDays] = useState<string[]>([])
  const [scheduleTime, setScheduleTime] = useState('08:00')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [itemOpen, setItemOpen] = useState(false)
  const itemRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (itemRef.current && !itemRef.current.contains(e.target as Node)) setItemOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  useEffect(() => {
    if (!shopId) { setLoadingItems(false); return }
    Promise.all([
      api.shops.items(shopId).catch(() => [] as Item[]),
      api.shops.get(shopId).catch(() => null),
    ]).then(([fetchedItems, fetchedShop]) => {
      setItems(fetchedItems)
      setShop(fetchedShop)
    }).finally(() => setLoadingItems(false))
  }, [shopId])

  if (!user || !isSeller) {
    return (
      <PageContainer narrow>
        <EmptyState
          message="Sign in as a seller to create recurring auctions"
          action={<Button onClick={() => navigate('/shop/login')}>Seller Sign In</Button>}
        />
      </PageContainer>
    )
  }

  if (!shopId) {
    return (
      <PageContainer narrow>
        <EmptyState
          message="No shop selected."
          action={<Button onClick={() => navigate('/')}>Go Home</Button>}
        />
      </PageContainer>
    )
  }

  const selectedItem = items.find((i) => i.item_id === itemId)

  const toggleDay = (day: string) => {
    setScheduleDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day]
    )
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!selectedItem) return
    setError(null)
    setLoading(true)
    try {
      await api.templates.create({
        item_id: selectedItem.item_id,
        item_title: selectedItem.title,
        shop_id: shopId,
        shop_name: shop?.name ?? '',
        shop_lat: shop?.lat,
        shop_lng: shop?.lng,
        retail_price: selectedItem.retail_value,
        image_url: selectedItem.image_url ?? '',
        shop_logo_url: shop?.logo_url ?? '',
        description: selectedItem.description ?? '',
        category: selectedItem.category ?? undefined,
        duration_minutes: parseInt(duration, 10),
        start_bid: Math.round(parseFloat(startBid) * 100),
        max_price: maxPrice ? Math.round(parseFloat(maxPrice) * 100) : undefined,
        min_increment: minIncrement ? Math.round(parseFloat(minIncrement) * 100) : undefined,
        quantity: parseInt(quantity, 10) > 1 ? parseInt(quantity, 10) : undefined,
        pickup_offset_minutes: parseInt(pickupOffset, 10),
        pickup_window_minutes: parseInt(pickupWindow, 10),
        schedule_type: scheduleType,
        schedule_days: scheduleType === 'weekly' ? scheduleDays.join(',') : undefined,
        schedule_time: scheduleTime,
      }, token!)
      navigate(`/seller/shops/${shopId}/templates`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  return (
    <PageContainer narrow>
      <Link
        to={`/seller/shops/${shopId}/templates`}
        className="inline-flex items-center gap-1 text-text-secondary hover:text-brand text-base font-medium transition-colors mb-8"
      >
        <ChevronLeftIcon /> Back to Templates
      </Link>

      <Card padding="p-8">
        <h1 className="font-display text-3xl text-text-primary mb-2">Create Recurring Auction</h1>
        <p className="text-text-secondary text-base mb-8">
          Set up a template to automatically publish auctions on a schedule.
        </p>

        {error && (
          <div className="mb-4">
            <StatusBanner type="error" message={error} />
          </div>
        )}

        {loadingItems ? (
          <Spinner className="py-10" />
        ) : items.length === 0 ? (
          <div className="text-center py-8 w-full">
            <p className="text-text-secondary mb-4">No items in your shop yet.</p>
            <div className="flex justify-center">
              <Button onClick={() => navigate(`/shops/${shopId}/items/new`)}>
                Add an Item First
              </Button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <FormField label="Select Item">
              <div ref={itemRef} className="relative">
                <button
                  type="button"
                  onClick={() => setItemOpen((o) => !o)}
                  className={`w-full flex items-center justify-between px-4 py-3 rounded-xl border-2 font-sans text-base transition-all bg-white ${itemOpen ? 'border-brand ring-2 ring-brand/20' : 'border-border hover:border-brand/50'} ${itemId ? 'text-text-primary' : 'text-text-secondary'}`}
                >
                  <span>{selectedItem ? `${selectedItem.title} (retail ${formatCurrency(selectedItem.retail_value)})` : '— choose an item —'}</span>
                  <svg className={`w-4 h-4 text-text-secondary shrink-0 transition-transform ${itemOpen ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>
                </button>
                {itemOpen && (
                  <ul className="absolute z-50 mt-1 w-full bg-white border border-border rounded-xl shadow-lg overflow-hidden">
                    {items.map((item) => (
                      <li
                        key={item.item_id}
                        onMouseDown={() => { setItemId(item.item_id); setItemOpen(false) }}
                        className={`px-4 py-2.5 text-base cursor-pointer transition-colors ${itemId === item.item_id ? 'bg-brand/10 text-brand font-medium' : 'text-text-primary hover:bg-brand/5'}`}
                      >
                        <span className="font-medium">{item.title}</span>
                        <span className="text-text-secondary text-sm ml-2">retail {formatCurrency(item.retail_value)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </FormField>

            {/* Schedule Section */}
            <div className="border-t border-border pt-4 mt-2">
              <h2 className="font-sans font-semibold text-lg text-text-primary mb-4">Schedule</h2>

              <FormField label="Frequency">
                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => setScheduleType('daily')}
                    className={`px-5 py-2.5 rounded-xl border-2 font-sans text-base font-medium transition-all ${scheduleType === 'daily' ? 'border-brand bg-brand/10 text-brand' : 'border-border text-text-secondary hover:border-brand/50'}`}
                  >
                    Daily
                  </button>
                  <button
                    type="button"
                    onClick={() => setScheduleType('weekly')}
                    className={`px-5 py-2.5 rounded-xl border-2 font-sans text-base font-medium transition-all ${scheduleType === 'weekly' ? 'border-brand bg-brand/10 text-brand' : 'border-border text-text-secondary hover:border-brand/50'}`}
                  >
                    Weekly
                  </button>
                </div>
              </FormField>

              {scheduleType === 'weekly' && (
                <FormField label="Days of the Week">
                  <div className="flex flex-wrap gap-2">
                    {DAYS.map((day) => (
                      <button
                        key={day}
                        type="button"
                        onClick={() => toggleDay(day)}
                        className={`px-4 py-2 rounded-lg border-2 font-sans text-sm font-medium transition-all ${scheduleDays.includes(day) ? 'border-brand bg-brand/10 text-brand' : 'border-border text-text-secondary hover:border-brand/50'}`}
                      >
                        {day}
                      </button>
                    ))}
                  </div>
                </FormField>
              )}

              <FormField label="Time (UTC)">
                <TextInput
                  type="time"
                  required
                  value={scheduleTime}
                  onChange={(e) => setScheduleTime(e.target.value)}
                />
                <p className="text-sm text-text-secondary mt-1">
                  Auction will be published at this time each scheduled day.
                </p>
              </FormField>
            </div>

            {/* Auction Settings */}
            <div className="border-t border-border pt-4 mt-2">
              <h2 className="font-sans font-semibold text-lg text-text-primary mb-4">Auction Settings</h2>

              <FormField label="Duration (minutes)">
                <TextInput
                  type="number"
                  required
                  min="1"
                  max="1440"
                  placeholder="5"
                  value={duration}
                  onChange={(e) => setDuration(e.target.value)}
                />
              </FormField>

              <FormField label="Starting Bid ($)">
                <TextInput
                  type="number"
                  required
                  min="0.01"
                  step="0.01"
                  placeholder="1.00"
                  value={startBid}
                  onChange={(e) => setStartBid(e.target.value)}
                />
              </FormField>

              <FormField label="Max Price / Bid Ceiling ($, optional)">
                <TextInput
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="Leave empty for no limit"
                  value={maxPrice}
                  onChange={(e) => setMaxPrice(e.target.value)}
                />
              </FormField>

              <FormField label="Minimum Bid Increment ($, optional)">
                <TextInput
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="Leave empty for no minimum raise"
                  value={minIncrement}
                  onChange={(e) => setMinIncrement(e.target.value)}
                />
              </FormField>

              <FormField label="Winners / Quantity (optional)">
                <TextInput
                  type="number"
                  min="1"
                  max="100"
                  placeholder="1"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                />
              </FormField>
            </div>

            {/* Pickup Settings */}
            <div className="border-t border-border pt-4 mt-2">
              <h2 className="font-sans font-semibold text-lg text-text-primary mb-4">Pickup Window</h2>

              <FormField label="Pickup starts after auction ends (minutes)">
                <TextInput
                  type="number"
                  required
                  min="1"
                  placeholder="30"
                  value={pickupOffset}
                  onChange={(e) => setPickupOffset(e.target.value)}
                />
                <p className="text-sm text-text-secondary mt-1">
                  How many minutes after the auction closes the pickup window opens.
                </p>
              </FormField>

              <FormField label="Pickup window length (minutes)">
                <TextInput
                  type="number"
                  required
                  min="1"
                  placeholder="60"
                  value={pickupWindow}
                  onChange={(e) => setPickupWindow(e.target.value)}
                />
                <p className="text-sm text-text-secondary mt-1">
                  How long the pickup window stays open.
                </p>
              </FormField>
            </div>

            <Button
              variant="primary"
              size="lg"
              type="submit"
              fullWidth
              disabled={loading || !itemId || !scheduleTime || (scheduleType === 'weekly' && scheduleDays.length === 0)}
              className="mt-2"
            >
              {loading ? 'Creating...' : 'Create Recurring Auction'}
            </Button>
          </form>
        )}
      </Card>
    </PageContainer>
  )
}
