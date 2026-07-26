import { PlateauAlertsList } from './PlateauAlertsList'
import { ProgressionSection } from './ProgressionSection'
import { VolumeSection } from './VolumeSection'

export function DashboardPage() {
  return (
    <div className="flex flex-col gap-8">
      <h1 className="font-display text-2xl font-bold text-ink">Dashboard</h1>
      <ProgressionSection />
      <VolumeSection />
      <section>
        <h2 className="font-display text-xl font-bold text-ink">Alertas de platô</h2>
        <PlateauAlertsList />
      </section>
    </div>
  )
}
