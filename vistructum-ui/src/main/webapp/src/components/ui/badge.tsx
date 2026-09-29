import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"
import { Slot } from "radix-ui"

const badgeVariants = cva(
  "inline-flex w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-[4px] px-1.5 py-0.5 text-xs font-medium whitespace-nowrap transition-[color,box-shadow] focus-visible:ring-[3px] focus-visible:ring-ring/50 [&>svg]:pointer-events-none [&>svg]:size-3",
  {
    variants: {
      variant: {
        amber: "bg-tag-amber text-tag-amber-foreground",
        rose: "bg-tag-rose text-tag-rose-foreground",
        green: "bg-tag-green text-tag-green-foreground",
        blue: "bg-tag-blue text-tag-blue-foreground",
        violet: "bg-tag-violet text-tag-violet-foreground",
        slate: "bg-tag-slate text-tag-slate-foreground",
      },
    },
    defaultVariants: {
      variant: "slate",
    },
  }
)

function Badge({
  className,
  variant = "slate",
  asChild = false,
  ...props
}: React.ComponentProps<"span"> &
  VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : "span"

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Badge, badgeVariants }
