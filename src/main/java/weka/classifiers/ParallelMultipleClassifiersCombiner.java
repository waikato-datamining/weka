/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    ParallelMultipleClassifiersCombiner.java
 *    Copyright (C) 2009-2012 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.classifiers;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import weka.core.Instances;
import weka.core.Option;
import weka.core.Utils;

/**
 * Abstract utility class for handling settings common to
 * meta classifiers that build an ensemble in parallel using multiple
 * classifiers.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision$
 */
public abstract class ParallelMultipleClassifiersCombiner extends
    MultipleClassifiersCombiner {

  /** For serialization */
  private static final long serialVersionUID = 728109028953726626L;

  /** The number of threads to have executing at any one time */
  protected int m_numExecutionSlots = 1;

  /** The number of classifiers completed so far */
  protected int m_completed;

  /**
   * The number of classifiers that experienced a failure of some sort
   * during construction
   */
  protected int m_failed;

  /**
   * Returns an enumeration describing the available options.
   *
   * @return an enumeration of all the available options.
   */
  public Enumeration<Option> listOptions() {

    Vector<Option> newVector = new Vector<Option>(1);

    newVector.addElement(new Option(
              "\tNumber of execution slots.\n"
              + "\t(default 1 - i.e. no parallelism)",
              "num-slots", 1, "-num-slots <num>"));

    newVector.addAll(Collections.list(super.listOptions()));
    
    return newVector.elements();
  }

  /**
   * Parses a given list of options. Valid options are:<p>
   *
   * -Z num <br>
   * Set the number of execution slots to use (default 1 - i.e. no parallelism). <p>
   *
   * Options after -- are passed to the designated classifier.<p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception {

    String iterations = Utils.getOption("num-slots", options);
    if (iterations.length() != 0) {
      setNumExecutionSlots(Integer.parseInt(iterations));
    } else {
      setNumExecutionSlots(1);
    }

    super.setOptions(options);
  }

  /**
   * Gets the current settings of the classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String [] getOptions() {

    Vector<String> options = new Vector<String>();

    options.add("-num-slots");
    options.add("" + getNumExecutionSlots());

    Collections.addAll(options, super.getOptions());
    
    return options.toArray(new String[0]);
  }

  /**
   * Set the number of execution slots (threads) to use for building the
   * members of the ensemble.
   *
   * @param numSlots the number of slots to use.
   */
  public void setNumExecutionSlots(int numSlots) {
    m_numExecutionSlots = numSlots;
  }

  /**
   * Get the number of execution slots (threads) to use for building
   * the members of the ensemble.
   *
   * @return the number of slots to use
   */
  public int getNumExecutionSlots() {
    return m_numExecutionSlots;
  }

  /**
   * Returns the tip text for this property
   * @return tip text for this property suitable for
   * displaying in the explorer/experimenter gui
   */
  public String numExecutionSlotsTipText() {
    return "The number of execution slots (threads) to use for " +
      "constructing the ensemble.";
  }

  /**
   * Stump method for building the classifiers
   *
   * @param data the training data to be used for generating the ensemble
   * @exception Exception if the classifier could not be built successfully
   */
  public void buildClassifier(Instances data) throws Exception {

    if (m_numExecutionSlots < 1) {
      throw new Exception("Number of execution slots needs to be >= 1!");
    }
    m_completed = 0;
    m_failed = 0;
  }

  /**
   * Does the actual construction of the ensemble
   *
   * @throws Exception if something goes wrong during the training
   * process
   */
  protected void buildClassifiers(final Instances data) throws Exception {

    if (m_numExecutionSlots <= 1) {
      for (int i = 0; i < m_Classifiers.length; i++) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        m_Classifiers[i].buildClassifier(data);
      }
      return;
    }

    ExecutorService executorPool = Executors.newFixedThreadPool(m_numExecutionSlots);

    final CountDownLatch doneSignal = new CountDownLatch(m_Classifiers.length);
    final AtomicInteger numFailed = new AtomicInteger();
    final AtomicInteger numCompleted = new AtomicInteger();

    for (int i = 0; i < m_Classifiers.length; i++) {
      final Classifier currentClassifier = m_Classifiers[i];
      if (currentClassifier == null) {
        doneSignal.countDown();
        continue;
      }
      final int iteration = i;

      if (m_Debug) {
        System.out.print("Training classifier (" + (i + 1) + ")");
      }
      Runnable newTask = new Runnable() {
        public void run() {
          try {
            if (Thread.currentThread().isInterrupted()) return;
            currentClassifier.buildClassifier(data);
            numCompleted.incrementAndGet();
          } catch (Throwable ex) {
            if (ex instanceof InterruptedException) return;
            numFailed.incrementAndGet();
            if (m_Debug) {
              System.err.println("Iteration " + iteration + " failed!");
              ex.printStackTrace();
            }
          } finally {
            doneSignal.countDown();
          }
        }
      };
      // launch this task
      executorPool.submit(newTask);
    }

    try {
      doneSignal.await();
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt(); // preserve cancellation signal
      throw ie; // propagate as "cancelled"
    } finally {
      executorPool.shutdownNow();
    }
    if (m_Debug && numFailed.intValue() > 0) {
      System.err.println("Problem building classifiers - some iterations failed.");
    }
    m_completed = numCompleted.intValue();
    m_failed = numFailed.intValue();
  }
}
