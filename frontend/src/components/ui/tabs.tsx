import * as React from 'react';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import { cn } from '@/lib/utils';

function Tabs({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Root>) {
  return <TabsPrimitive.Root data-slot="tabs" className={cn('flex flex-col gap-2', className)} {...props} />;
}

function TabsList({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.List>) {
  return <TabsPrimitive.List data-slot="tabs-list" className={cn(
    'inline-flex w-fit flex-wrap items-center justify-center gap-1 rounded-lg border border-border bg-muted p-1 text-muted-foreground',
    className,
  )} {...props} />;
}

function TabsTrigger({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return <TabsPrimitive.Trigger data-slot="tabs-trigger" className={cn(
    'm-0 inline-flex min-h-11 items-center justify-center gap-1.5 rounded-md border border-transparent bg-transparent px-4 py-2 text-sm font-medium whitespace-nowrap text-muted-foreground outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-foreground data-[state=active]:text-white data-[state=active]:shadow-sm',
    className,
  )} {...props} />;
}

function TabsContent({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Content>) {
  return <TabsPrimitive.Content data-slot="tabs-content" className={cn(
    'flex-1 outline-none focus-visible:ring-[3px] focus-visible:ring-ring', className,
  )} {...props} />;
}

export { Tabs, TabsList, TabsTrigger, TabsContent };
