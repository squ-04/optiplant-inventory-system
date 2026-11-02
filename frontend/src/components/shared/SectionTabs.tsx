/**
 * Tabs of one section. Every panel stays mounted and is only hidden, so a form keeps what was typed when changing tabs;
 * `onSelect` lets the section read again what the opened panel shows.
 */
import { useState } from 'react';
import type { ReactNode } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../ui/tabs';

export function SectionTabs({ label, items, onSelect }: {
  label: string;
  onSelect?: (index: number) => void;
  items: { label: string; content: ReactNode }[];
}) {
  const [active, setActive] = useState('0');
  return <Tabs value={active} onValueChange={value => { setActive(value); onSelect?.(Number(value)); }} className="mt-6">
    <TabsList aria-label={label}>
      {items.map((item, index) => <TabsTrigger key={item.label} value={String(index)}>{item.label}</TabsTrigger>)}
    </TabsList>
    {items.map((item, index) => <TabsContent key={item.label} value={String(index)}
      forceMount hidden={active !== String(index)}>{item.content}</TabsContent>)}
  </Tabs>;
}
