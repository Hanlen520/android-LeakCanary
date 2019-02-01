/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.leakcanary;

import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Field;
import com.squareup.haha.perflib.HprofParser;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.RootObj;
import com.squareup.haha.perflib.RootType;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.Type;
import com.squareup.haha.perflib.io.HprofBuffer;
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer;
import com.squareup.haha.trove.THashMap;
import com.squareup.haha.trove.TObjectProcedure;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N_MR1;
import static com.squareup.leakcanary.AnalysisResult.failure;
import static com.squareup.leakcanary.AnalysisResult.leakDetected;
import static com.squareup.leakcanary.AnalysisResult.noLeak;
import static com.squareup.leakcanary.HahaHelper.asString;
import static com.squareup.leakcanary.HahaHelper.classInstanceValues;
import static com.squareup.leakcanary.HahaHelper.extendsThread;
import static com.squareup.leakcanary.HahaHelper.fieldToString;
import static com.squareup.leakcanary.HahaHelper.fieldValue;
import static com.squareup.leakcanary.HahaHelper.hasField;
import static com.squareup.leakcanary.HahaHelper.threadName;
import static com.squareup.leakcanary.LeakTraceElement.Holder.ARRAY;
import static com.squareup.leakcanary.LeakTraceElement.Holder.CLASS;
import static com.squareup.leakcanary.LeakTraceElement.Holder.OBJECT;
import static com.squareup.leakcanary.LeakTraceElement.Holder.THREAD;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Analyzes heap dumps generated by a {@link RefWatcher} to verify if suspected leaks are real.
 */
public final class HeapAnalyzer {

  private static final String ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$";

  private final ExcludedRefs excludedRefs;

  public HeapAnalyzer(ExcludedRefs excludedRefs) {
    this.excludedRefs = excludedRefs;
  }

  public List<TrackedReference> findTrackedReferences(File heapDumpFile) {
    if (!heapDumpFile.exists()) {
      throw new IllegalArgumentException("File does not exist: " + heapDumpFile);
    }
    try {
      HprofBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
      HprofParser parser = new HprofParser(buffer);
      Snapshot snapshot = parser.parse();
      deduplicateGcRoots(snapshot);

      ClassObj refClass = snapshot.findClass(KeyedWeakReference.class.getName());
      List<TrackedReference> references = new ArrayList<>();
      for (Instance weakRef : refClass.getInstancesList()) {
        List<ClassInstance.FieldValue> values = classInstanceValues(weakRef);
        String key = asString(fieldValue(values, "key"));
        String name =
            hasField(values, "name") ? asString(fieldValue(values, "name")) : "(No name field)";
        Instance instance = fieldValue(values, "referent");
        if (instance != null) {
          String className = getClassName(instance);
          List<String> fields = describeFields(instance);
          references.add(new TrackedReference(key, name, className, fields));
        }
      }
      return references;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Searches the heap dump for a {@link KeyedWeakReference} instance with the corresponding key,
   * and then computes the shortest strong reference path from that instance to the GC roots.
   */
  public AnalysisResult checkForLeak(File heapDumpFile, String referenceKey) {
    long analysisStartNanoTime = System.nanoTime();

    if (!heapDumpFile.exists()) {
      Exception exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
      return failure(exception, since(analysisStartNanoTime));
    }

    try {
      HprofBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
      HprofParser parser = new HprofParser(buffer);
      Snapshot snapshot = parser.parse();
      deduplicateGcRoots(snapshot);

      Instance leakingRef = findLeakingReference(referenceKey, snapshot);

      // False alarm, weak reference was cleared in between key check and heap dump.
      if (leakingRef == null) {
        return noLeak(since(analysisStartNanoTime));
      }

      return findLeakTrace(analysisStartNanoTime, snapshot, leakingRef);
    } catch (Throwable e) {
      return failure(e, since(analysisStartNanoTime));
    }
  }

  /**
   * Pruning duplicates reduces memory pressure from hprof bloat added in Marshmallow.
   */
  void deduplicateGcRoots(Snapshot snapshot) {
    // THashMap has a smaller memory footprint than HashMap.
    final THashMap<String, RootObj> uniqueRootMap = new THashMap<>();

    final Collection<RootObj> gcRoots = snapshot.getGCRoots();
    for (RootObj root : gcRoots) {
      String key = generateRootKey(root);
      if (!uniqueRootMap.containsKey(key)) {
        uniqueRootMap.put(key, root);
      }
    }

    // Repopulate snapshot with unique GC roots.
    gcRoots.clear();
    uniqueRootMap.forEach(new TObjectProcedure<String>() {
      @Override public boolean execute(String key) {
        return gcRoots.add(uniqueRootMap.get(key));
      }
    });
  }

  private String generateRootKey(RootObj root) {
    return String.format("%s@0x%08x", root.getRootType().getName(), root.getId());
  }

  private Instance findLeakingReference(String key, Snapshot snapshot) {
    ClassObj refClass = snapshot.findClass(KeyedWeakReference.class.getName());
    if(refClass == null){
      return null;
    }
    List<String> keysFound = new ArrayList<>();
    for (Instance instance : refClass.getInstancesList()) {
      List<ClassInstance.FieldValue> values = classInstanceValues(instance);
      String keyCandidate = asString(fieldValue(values, "key"));
      if (keyCandidate.equals(key)) {
        return fieldValue(values, "referent");
      }
      keysFound.add(keyCandidate);
    }
    throw new IllegalStateException(
        "Could not find weak reference with key " + key + " in " + keysFound);
  }

  private AnalysisResult findLeakTrace(long analysisStartNanoTime, Snapshot snapshot,
      Instance leakingRef) {

    ShortestPathFinder pathFinder = new ShortestPathFinder(excludedRefs);
    ShortestPathFinder.Result result = pathFinder.findPath(snapshot, leakingRef);

    // False alarm, no strong reference path to GC Roots.
    if (result.leakingNode == null) {
      return noLeak(since(analysisStartNanoTime));
    }

    LeakTrace leakTrace = buildLeakTrace(result.leakingNode);

    String className = leakingRef.getClassObj().getClassName();

    // Side effect: computes retained size.
    snapshot.computeDominators();

    Instance leakingInstance = result.leakingNode.instance;

    long retainedSize = leakingInstance.getTotalRetainedSize();

    // TODO: check O sources and see what happened to android.graphics.Bitmap.mBuffer
    if (SDK_INT <= N_MR1) {
      retainedSize += computeIgnoredBitmapRetainedSize(snapshot, leakingInstance);
    }

    return leakDetected(result.excludingKnownLeaks, className, leakTrace, retainedSize,
        since(analysisStartNanoTime));
  }

  /**
   * Bitmaps and bitmap byte arrays are sometimes held by native gc roots, so they aren't included
   * in the retained size because their root dominator is a native gc root.
   * To fix this, we check if the leaking instance is a dominator for each bitmap instance and then
   * add the bitmap size.
   *
   * From experience, we've found that bitmap created in code (Bitmap.createBitmap()) are correctly
   * accounted for, however bitmaps set in layouts are not.
   */
  private long computeIgnoredBitmapRetainedSize(Snapshot snapshot, Instance leakingInstance) {
    long bitmapRetainedSize = 0;
    ClassObj bitmapClass = snapshot.findClass("android.graphics.Bitmap");

    for (Instance bitmapInstance : bitmapClass.getInstancesList()) {
      if (isIgnoredDominator(leakingInstance, bitmapInstance)) {
        ArrayInstance mBufferInstance = fieldValue(classInstanceValues(bitmapInstance), "mBuffer");
        // Native bitmaps have mBuffer set to null. We sadly can't account for them.
        if (mBufferInstance == null) {
          continue;
        }
        long bufferSize = mBufferInstance.getTotalRetainedSize();
        long bitmapSize = bitmapInstance.getTotalRetainedSize();
        // Sometimes the size of the buffer isn't accounted for in the bitmap retained size. Since
        // the buffer is large, it's easy to detect by checking for bitmap size < buffer size.
        if (bitmapSize < bufferSize) {
          bitmapSize += bufferSize;
        }
        bitmapRetainedSize += bitmapSize;
      }
    }
    return bitmapRetainedSize;
  }

  private boolean isIgnoredDominator(Instance dominator, Instance instance) {
    boolean foundNativeRoot = false;
    while (true) {
      Instance immediateDominator = instance.getImmediateDominator();
      if (immediateDominator instanceof RootObj
          && ((RootObj) immediateDominator).getRootType() == RootType.UNKNOWN) {
        // Ignore native roots
        instance = instance.getNextInstanceToGcRoot();
        foundNativeRoot = true;
      } else {
        instance = immediateDominator;
      }
      if (instance == null) {
        return false;
      }
      if (instance == dominator) {
        return foundNativeRoot;
      }
    }
  }

  private LeakTrace buildLeakTrace(LeakNode leakingNode) {
    List<LeakTraceElement> elements = new ArrayList<>();
    // We iterate from the leak to the GC root
    LeakNode node = new LeakNode(null, null, leakingNode, null, null);
    while (node != null) {
      LeakTraceElement element = buildLeakElement(node);
      if (element != null) {
        elements.add(0, element);
      }
      node = node.parent;
    }
    return new LeakTrace(elements);
  }

  private LeakTraceElement buildLeakElement(LeakNode node) {
    if (node.parent == null) {
      // Ignore any root node.
      return null;
    }
    Instance holder = node.parent.instance;

    if (holder instanceof RootObj) {
      return null;
    }
    LeakTraceElement.Type type = node.referenceType;
    String referenceName = node.referenceName;

    LeakTraceElement.Holder holderType;
    String className;
    String extra = null;
    List<String> fields = describeFields(holder);

    className = getClassName(holder);

    if (holder instanceof ClassObj) {
      holderType = CLASS;
    } else if (holder instanceof ArrayInstance) {
      holderType = ARRAY;
    } else {
      ClassObj classObj = holder.getClassObj();
      if (extendsThread(classObj)) {
        holderType = THREAD;
        String threadName = threadName(holder);
        extra = "(named '" + threadName + "')";
      } else if (className.matches(ANONYMOUS_CLASS_NAME_PATTERN)) {
        String parentClassName = classObj.getSuperClassObj().getClassName();
        if (Object.class.getName().equals(parentClassName)) {
          holderType = OBJECT;
          try {
            // This is an anonymous class implementing an interface. The API does not give access
            // to the interfaces implemented by the class. We check if it's in the class path and
            // use that instead.
            Class<?> actualClass = Class.forName(classObj.getClassName());
            Class<?>[] interfaces = actualClass.getInterfaces();
            if (interfaces.length > 0) {
              Class<?> implementedInterface = interfaces[0];
              extra = "(anonymous implementation of " + implementedInterface.getName() + ")";
            } else {
              extra = "(anonymous subclass of java.lang.Object)";
            }
          } catch (ClassNotFoundException ignored) {
          }
        } else {
          holderType = OBJECT;
          // Makes it easier to figure out which anonymous class we're looking at.
          extra = "(anonymous subclass of " + parentClassName + ")";
        }
      } else {
        holderType = OBJECT;
      }
    }
    return new LeakTraceElement(referenceName, type, holderType, className, extra, node.exclusion,
        fields);
  }

  private List<String> describeFields(Instance instance) {
    List<String> fields = new ArrayList<>();

    if (instance instanceof ClassObj) {
      ClassObj classObj = (ClassObj) instance;
      for (Map.Entry<Field, Object> entry : classObj.getStaticFieldValues().entrySet()) {
        Field field = entry.getKey();
        Object value = entry.getValue();
        fields.add("static " + field.getName() + " = " + value);
      }
    } else if (instance instanceof ArrayInstance) {
      ArrayInstance arrayInstance = (ArrayInstance) instance;
      if (arrayInstance.getArrayType() == Type.OBJECT) {
        Object[] values = arrayInstance.getValues();
        for (int i = 0; i < values.length; i++) {
          fields.add("[" + i + "] = " + values[i]);
        }
      }
    } else {
      ClassObj classObj = instance.getClassObj();
      for (Map.Entry<Field, Object> entry : classObj.getStaticFieldValues().entrySet()) {
        fields.add("static " + fieldToString(entry));
      }
      ClassInstance classInstance = (ClassInstance) instance;
      for (ClassInstance.FieldValue field : classInstance.getValues()) {
        fields.add(fieldToString(field));
      }
    }
    return fields;
  }

  private String getClassName(Instance instance) {
    String className;
    if (instance instanceof ClassObj) {
      ClassObj classObj = (ClassObj) instance;
      className = classObj.getClassName();
    } else if (instance instanceof ArrayInstance) {
      ArrayInstance arrayInstance = (ArrayInstance) instance;
      className = arrayInstance.getClassObj().getClassName();
    } else {
      ClassObj classObj = instance.getClassObj();
      className = classObj.getClassName();
    }
    return className;
  }

  private long since(long analysisStartNanoTime) {
    return NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
  }
}
