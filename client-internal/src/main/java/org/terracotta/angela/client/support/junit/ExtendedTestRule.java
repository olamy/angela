/*
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Angela.
 *
 * The Initial Developer of the Covered Software is
 * Terracotta, Inc., a Software AG company
 */
package org.terracotta.angela.client.support.junit;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mathieu Carbou
 */
public abstract class ExtendedTestRule implements TestRule {
  @Override
  public final Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        before(description);
        List<Throwable> errors = new ArrayList<>();
        try {
          base.evaluate();
        } catch (Throwable t) {
          errors.add(t);
        } finally {
          try {
            after(description);
          } catch (Throwable t) {
            errors.add(t);
          }
        }
        MultipleFailureException.assertEmpty(errors);
      }
    };
  }

  protected void before(Description description) throws Throwable {
  }

  protected void after(Description description) throws Throwable {
  }
}