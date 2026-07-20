import localUnitTest from './LocalUnit.test';
import feedbackClientTest from './FeedbackClient.test';
import qzxyLoginClientTest from './QzxyLoginClient.test';

export default function testsuite() {
  localUnitTest();
  feedbackClientTest();
  qzxyLoginClientTest();
}
