export default function DateDivider({ label }: { label: string }) {
  return (
    <div className="my-4 flex justify-center">
      <span className="rounded-full bg-surface-container px-3 py-1 text-xs font-semibold text-on-surface-variant shadow-sm">
        {label}
      </span>
    </div>
  );
}
