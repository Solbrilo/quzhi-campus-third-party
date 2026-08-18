import localUnitTest from './LocalUnit.test';
import feedbackClientTest from './FeedbackClient.test';
import feedbackHistoryStoreTest from './FeedbackHistoryStore.test';
import qzxyLoginClientTest from './QzxyLoginClient.test';

export default function testsuite() {
  localUnitTest();
  feedbackClientTest();
  feedbackHistoryStoreTest();
  qzxyLoginClientTest();
}
