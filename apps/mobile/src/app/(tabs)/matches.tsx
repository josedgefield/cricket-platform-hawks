import { ComingSoon } from '@/components/coming-soon';

export default function MatchesScreen() {
  return (
    <ComingSoon
      title="Matches"
      what={['Fixtures and results across SCA, BPL and IAT30', 'Tell the captain if you are available', 'Add fixtures to your calendar']}
      needs="member sign-in, and fixture data from SCA and CricHeroes scorecards."
    />
  );
}
