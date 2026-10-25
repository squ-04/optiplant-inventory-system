import { useState } from 'react';
import type { ReactNode } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../ui/tabs';

export function SectionTabs({ label, items }: {
  label: string;
  items: { label: string; content: ReactNode }[];
}) {
  const [active, setActive] = useState('0');
  return <Tabs value={active} onValueChange={setActive} className="mt-6">
    <TabsList aria-label={label}>
      {items.map((item, index) => <TabsTrigger key={item.label} value={String(index)}>{item.label}</TabsTrigger>)}
    </TabsList>
    {items.map((item, index) => <TabsContent key={item.label} value={String(index)}
      forceMount hidden={active !== String(index)}>{item.content}</TabsContent>)}
  </Tabs>;
}
