import { useState, useRef, useEffect } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { api } from '@/lib/api'
import { CATEGORIES } from '@/types'
import { Card, Button, FormField, TextInput, TextArea, StatusBanner, EmptyState, ImageUpload } from '@/components/ui'
import { PageContainer } from '@/components/layout'
import { ChevronLeftIcon } from '@/components/icons'

export default function CreateItemPage() {
  const { shopId }      = useParams<{ shopId: string }>()
  const { user, token, isSeller } = useAuth()
  const navigate        = useNavigate()

  const [title,       setTitle]       = useState('')
  const [description, setDescription] = useState('')
  const [retailValue, setRetailValue] = useState('')
  const [imageUrl,    setImageUrl]    = useState('')
  const [category,    setCategory]    = useState('')
  const [loading,     setLoading]     = useState(false)
  const [aiLoading,   setAiLoading]   = useState(false)
  const [error,       setError]       = useState<string | null>(null)
  const [catOpen,     setCatOpen]     = useState(false)
  const catRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (catRef.current && !catRef.current.contains(e.target as Node)) setCatOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const handleGenerate = async () => {
    setAiLoading(true)
    setError(null)
    try {
      const desc = await api.ai.describe(
        title,
        category || undefined,
        retailValue ? Math.round(parseFloat(retailValue) * 100) : undefined,
        token!,
      )
      setDescription(desc)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI generation failed')
    } finally {
      setAiLoading(false)
    }
  }

  if (!user || !isSeller) {
    return (
      <PageContainer narrow>
        <EmptyState
          message="Sign in as a seller to add items"
          action={<Button onClick={() => navigate('/shop/login')}>Seller Sign In</Button>}
        />
      </PageContainer>
    )
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await api.shops.createItem(
        shopId!,
        {
          title,
          description,
          retail_value: Math.round(parseFloat(retailValue) * 100),
          image_url:    imageUrl  || undefined,
          category:     category  || undefined,
        },
        token!,
      )
      navigate(`/seller/shops/${shopId}/auctions`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  return (
    <PageContainer narrow>
      <Link
        to={`/seller/shops/${shopId}/auctions`}
        className="inline-flex items-center gap-1 text-text-secondary hover:text-brand text-base font-medium transition-colors mb-8"
      >
        <ChevronLeftIcon /> Back to Shop
      </Link>

      <Card padding="p-8">
        <h1 className="font-display text-3xl text-text-primary mb-2">Add Item</h1>
        <p className="text-text-secondary text-base mb-8">
          List a product that can be auctioned when you have surplus stock.
        </p>

        {error && (
          <div className="mb-4">
            <StatusBanner type="error" message={error} />
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <FormField label="Item Title">
            <TextInput
              type="text"
              required
              placeholder="Mystery Pastry Box (3 items)"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </FormField>

          <FormField label={
            <span className="flex items-center justify-between w-full">
              <span>Description</span>
              <button
                type="button"
                onClick={handleGenerate}
                disabled={!title.trim() || aiLoading}
                className="inline-flex items-center gap-1.5 px-3 py-1 text-sm font-medium rounded-lg bg-brand/10 text-brand hover:bg-brand/20 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                {aiLoading ? (
                  <>
                    <svg className="w-3.5 h-3.5 animate-spin" viewBox="0 0 24 24" fill="none"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"/></svg>
                    Generating…
                  </>
                ) : (
                  <>
                    <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"><path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z"/></svg>
                    Generate with AI
                  </>
                )}
              </button>
            </span>
          }>
            <TextArea
              rows={3}
              placeholder="Describe the item — contents, freshness, best-before, etc."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </FormField>

          <FormField label="Retail Value ($)">
            <TextInput
              type="number"
              required
              min="0.01"
              step="0.01"
              placeholder="28.00"
              value={retailValue}
              onChange={(e) => setRetailValue(e.target.value)}
            />
          </FormField>

          <FormField label="Category">
            <div ref={catRef} className="relative">
              <button
                type="button"
                onClick={() => setCatOpen((o) => !o)}
                className={`w-full flex items-center justify-between px-4 py-3 rounded-xl border-2 font-sans text-base transition-all bg-white ${catOpen ? 'border-brand ring-2 ring-brand/20' : 'border-border hover:border-brand/50'} ${category ? 'text-text-primary' : 'text-text-secondary'}`}
              >
                <span>{category || '— select a category —'}</span>
                <svg className={`w-4 h-4 text-text-secondary transition-transform ${catOpen ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>
              </button>
              {catOpen && (
                <ul className="absolute z-50 mt-1 w-full bg-white border border-border rounded-xl shadow-lg overflow-hidden">
                  {CATEGORIES.map((c) => (
                    <li
                      key={c}
                      onMouseDown={() => { setCategory(c); setCatOpen(false) }}
                      className={`px-4 py-2.5 text-base cursor-pointer transition-colors ${category === c ? 'bg-brand/10 text-brand font-medium' : 'text-text-primary hover:bg-brand/5'}`}
                    >
                      {c}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </FormField>

          <FormField label="Item Image (optional)">
            <ImageUpload
              value={imageUrl}
              onChange={setImageUrl}
              token={token}
              label="Item Image"
            />
          </FormField>

          <Button variant="primary" size="lg" type="submit" fullWidth disabled={loading} className="mt-2">
            {loading ? 'Saving…' : 'Add Item'}
          </Button>
        </form>
      </Card>
    </PageContainer>
  )
}
