import { ComingSoon } from '@/components/coming-soon';

export default function MoreScreen() {
  return (
    <ComingSoon
      title="More"
      what={['Club notices with replies and reactions', 'Notification settings', 'Your profile, privacy and account deletion']}
      needs="member sign-in and the communications module."
    />
  );
}
