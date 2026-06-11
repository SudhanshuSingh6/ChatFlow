export default function DateDivider({ label }: { label: string }) {
  return (
    <div className="my-3 flex justify-center">
      <span className="rounded-full bg-gray-200/70 px-3 py-1 text-xs font-medium text-gray-500">
        {label}
      </span>
    </div>
  );
}
