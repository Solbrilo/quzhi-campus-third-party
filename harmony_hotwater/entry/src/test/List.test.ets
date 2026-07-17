import localUnitTest from './LocalUnit.test';
import feedbackClientTest from './FeedbackClient.test';

export default function testsuite() {
  localUnitTest();
  feedbackClientTest();
}
