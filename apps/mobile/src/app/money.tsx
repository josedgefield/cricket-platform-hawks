import { ComingSoon } from '@/components/coming-soon';

export default function MoneyScreen() {
  return (
    <ComingSoon
      title="Money"
      what={['What you owe, item by item', 'Pay by PayNow QR and mark it paid', 'Your statement and receipts']}
      needs="member sign-in, the finance ledger, and the club's fee structure and Excel sample."
    />
  );
}
