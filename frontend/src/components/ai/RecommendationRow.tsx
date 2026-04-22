import { useState, useEffect, useRef } from 'react'
import type { Auction } from '@/types'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { AuctionCard } from '@/components/auction'

interface Recommendation {
  auction: Auction
  reason: string
  score: number
}

interface Props {
  userCoords?: { lat: number; lng: number }
}

export function RecommendationRow({ userCoords }: Props) {
  const { user, token } = useAuth()
  const [recs, setRecs] = useState<Recommendation[]>([])
  const [loading, setLoading] = useState(true)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!token || user?.role !== 'buyer') {
      setLoading(false)
      return
    }
    api.ai
      .recommendations(token, userCoords ? { lat: userCoords.lat, lng: userCoords.lng } : undefined)
      .then(setRecs)
      .catch(() => setRecs([]))
      .finally(() => setLoading(false))
  }, [token, user, userCoords])

  if (loading || recs.length === 0) return null

  const scroll = (dir: 'left' | 'right') => {
    scrollRef.current?.scrollBy({ left: dir === 'left' ? -320 : 320, behavior: 'smooth' })
  }

  return (
    <div className="mb-10">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="font-sans font-semibold text-xl text-text-primary">Recommended for You</h2>
          <p className="text-text-secondary text-sm mt-0.5">Auctions picked based on your bidding activity</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => scroll('left')}
            className="w-8 h-8 rounded-full border border-border flex items-center justify-center text-text-secondary hover:text-text-primary hover:border-brand transition-colors"
            aria-label="Scroll left"
          >
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} className="w-4 h-4">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
          <button
            onClick={() => scroll('right')}
            className="w-8 h-8 rounded-full border border-border flex items-center justify-center text-text-secondary hover:text-text-primary hover:border-brand transition-colors"
            aria-label="Scroll right"
          >
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} className="w-4 h-4">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
        </div>
      </div>
      <div ref={scrollRef} className="flex gap-5 overflow-x-auto pb-2 scrollbar-hide snap-x snap-mandatory">
        {recs.map((rec) => (
          <div key={rec.auction.auction_id} className="min-w-[300px] max-w-[300px] snap-start flex flex-col">
            <AuctionCard auction={rec.auction} userCoords={userCoords} />
            <div className="mt-2 flex items-start gap-1.5 px-1">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} className="w-3.5 h-3.5 text-brand mt-0.5 flex-shrink-0">
                <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
              </svg>
              <span className="text-text-secondary text-xs leading-snug">{rec.reason}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
