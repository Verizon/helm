package helm

import org.scalatest.exceptions.TestFailedException

trait TestFramework {
  type T <: Throwable

  /**
   * Generate a test failure exception with stack offset `offset`.
   */
  def failed(msg: String, offset: Int = 0): T

  def withMessage(t: T, msg: String): T
}

object TestFramework {
  implicit object ScalaTestFramework extends TestFramework {
    type T = TestFailedException

    def failed(msg: String, offset: Int): TestFailedException =
      new TestFailedException(msg, offset + 2)

    def withMessage(f: TestFailedException, msg: String) =
      f modifyMessage { _ =>
        Some(msg)
      }
  }
}
