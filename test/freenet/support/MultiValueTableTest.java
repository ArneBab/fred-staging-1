/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package freenet.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

/**
 * Test case for {@link freenet.support.MultiValueTable} class.
 *
 * @author Alberto Bacchelli &lt;sback@freenetproject.org&gt;
 */
public class MultiValueTableTest {

    private static final int sampleKeyNumber = 100;
    private static final int sampleMaxValueNumber = 3;
    private static final boolean sampleIsRandom = true;
    private static final int NON_EXISTING_KEY = -42;
    private final Random rnd = new Random(12345);

    private Map<Integer, List<Object>> sampleObjects;
    private MultiValueTable<Integer, Object> multiValueTable;


    @Before
    public void setUp() throws Exception {
        sampleObjects = createSampleKeyMultiVal(sampleKeyNumber, sampleMaxValueNumber, sampleIsRandom);
        multiValueTable = fillMultiValueTable(sampleObjects);
    }

    /**
     * Create a Map filled with increasing Integers as keys
     * and a List of generic Objects as values.
     *
     * @param keysCount   the number of keys to create
     * @param valuesCount the maximum value number per key
     * @param isRandom    if true each key could have [1,valuesCount] values
     *                    chosen randomly, if false each key will have valuesCount values
     * @return the Map created
     */
    private Map<Integer, List<Object>> createSampleKeyMultiVal(int keysCount, int valuesCount, boolean isRandom) {
        Map<Integer, List<Object>> sampleObjects = new LinkedHashMap<>(keysCount);
        int methodValuesNumber = valuesCount;
        for (int i = 0; i < keysCount; i++) {
            if (isRandom) {
                methodValuesNumber = 1 + rnd.nextInt(valuesCount);
            }
            sampleObjects.put(i, fillSampleValuesList(methodValuesNumber));
        }
        return sampleObjects;
    }

    /**
     * Create a sample List filled
     * with the specified number of
     * generic objects
     *
     * @param valuesCount number of objects to create
     * @return the sample List
     */
    private static List<Object> fillSampleValuesList(int valuesCount) {
        List<Object> sampleValues = new ArrayList<>(valuesCount);
        for (int i = 0; i < valuesCount; i++) {
            sampleValues.add(new Object());
        }
        return sampleValues;
    }

    /**
     * Fill a new MultiValueTable from a Object[][] provided.
     * The Object[][] must be in the same form generated by
     * createSampleKeyMultiVal method.
     *
     * @param sampleObjects Object[][] array, with [i][0] as key and [i][1] as list of values
     * @return the created MultiValueTable
     */
    private MultiValueTable<Integer, Object> fillMultiValueTable(Map<Integer, List<Object>> sampleObjects) {
        MultiValueTable<Integer, Object> multiValueTable = new MultiValueTable<>(sampleObjects.size());
        for (Map.Entry<Integer, List<Object>> entries : sampleObjects.entrySet()) {
            multiValueTable.putAll(entries.getKey(), entries.getValue());
        }
        return multiValueTable;
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromThrowsExceptionWhenKeysAndValuesArrayParamsHaveDifferentSize() {
        MultiValueTable.from(
            new Integer[] {1, 2, 3},
            new String[] {"one", "two"}
        );
    }

    @Test
    public void testFromMethodWithArrayParams() {
        Integer[] keys = {1, 2, 3};
        String[] values = {"one", "two", "three"};
        MultiValueTable<Integer, String> table = MultiValueTable.from(keys, values);
        assertEquals(3, table.keySet().size());
        for (int i = 0; i < keys.length; i++) {
            assertEquals(values[i], table.get(keys[i]));
        }

        assertArrayEquals(keys, table.keySet().toArray(new Integer[0]));
        assertArrayEquals(values, table.values().toArray());
    }

    @Test
    public void testFromMethodWithVarArgParams() {
        MultiValueTable<String, Integer> table = MultiValueTable.from("key", 1, 2, 3);
        assertEquals(1, table.keySet().size());
        assertEquals("key", table.keySet().iterator().next());
        assertEquals(Arrays.asList(1, 2, 3), table.getAllAsList("key"));
    }

    @Test
    public void testPut() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        multiValueTable.put(1, "two");
        multiValueTable.put(3, "three");
        multiValueTable.put(4, "four");
        assertEquals(3, multiValueTable.size());
        assertEquals(2, multiValueTable.countAll(1));
        assertEquals(Arrays.asList("one", "two"), multiValueTable.getAllAsList(1));
        assertEquals(Collections.singletonList("three"), multiValueTable.getAllAsList(3));
        assertEquals(Collections.singletonList("four"), multiValueTable.getAllAsList(4));
    }

    @Test
    public void testPutAll() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, Arrays.asList("one", "two"));
        multiValueTable.putAll(2, Arrays.asList("three", "four"));
        multiValueTable.putAll(2, Arrays.asList("five", "six"));
        multiValueTable.putAll(3, Collections.emptyList());

        assertEquals(Arrays.asList("one", "two"), multiValueTable.getAllAsList(1));
        assertEquals(Arrays.asList("three", "four", "five", "six"), multiValueTable.getAllAsList(2));
        assertTrue(multiValueTable.getAllAsList(3).isEmpty());
    }

    @Test
    public void getFirstReturnsNullForNonExistingKey() {
        MultiValueTable<Integer, Object> table = new MultiValueTable<>();
        assertNull(table.getFirst(NON_EXISTING_KEY));
    }

    @Test
    public void testGetFirst() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertEquals(
                entry.getValue().get(0),
                multiValueTable.getFirst(entry.getKey())
            );
        }
    }

    @Test
    public void testGet() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertEquals(
                entry.getValue().get(0),
                multiValueTable.get(entry.getKey())
            );
        }
    }

    @Test
    public void containsKeyReturnsFalseForNonExistingKey() {
        assertFalse(multiValueTable.containsKey(NON_EXISTING_KEY));
    }

    @Test
    public void testContainsKey() {
        for (Integer key : sampleObjects.keySet()) {
            assertTrue(multiValueTable.containsKey(key));
        }
    }

    @Test
    public void containsElementReturnsFalseForNonExistingKey() {
        assertFalse(multiValueTable.containsElement(NON_EXISTING_KEY, new Object()));
    }


    @Test
    public void testContainsElement() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            for (Object element : entry.getValue()) {
                assertTrue(
                    multiValueTable.containsElement(entry.getKey(), element)
                );
            }
        }
    }

    @Test
    public void getAllMethodReturnsEmptyEnumerationForNonExistentKey() {
        Enumeration<Object> elements = multiValueTable.getAll(NON_EXISTING_KEY);
        assertNotNull(elements);
        assertFalse(elements.hasMoreElements());
    }

    @Test
    public void getAllMethodReturnsImmutableEnumeration() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, Arrays.asList("one", "two"));

        Enumeration<Object> enumeration = multiValueTable.getAll(1);

        multiValueTable.put(1, "three");

        int count = 0;
        while (enumeration.hasMoreElements()) {
            enumeration.nextElement();
            ++count;
        }
        assertEquals(2, count);
    }

    @Test
    public void testGetAll() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            Enumeration<Object> actualElements = multiValueTable.getAll(entry.getKey());
            for (Object element:  entry.getValue()) {
                assertTrue(actualElements.hasMoreElements());
                assertEquals(element, actualElements.nextElement());
            }
            assertFalse(actualElements.hasMoreElements());
        }
    }

    @Test
    public void testGetAllAsList() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertEquals(entry.getValue(), multiValueTable.getAllAsList(entry.getKey()));
        }
    }

    @Test
    public void getAllAsListMethodReturnsImmutableCollection() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, Arrays.asList("one", "two"));

        List<Object> list = multiValueTable.getAllAsList(1);

        multiValueTable.put(1, "three");

        assertEquals(2, list.size());

        assertThrows(UnsupportedOperationException.class, () -> list.add("four"));
        assertEquals(3, multiValueTable.getAllAsList(1).size());
    }

    @Test
    public void testGetArray() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertArrayEquals(
                entry.getValue().toArray(),
                multiValueTable.getArray(entry.getKey())
            );
        }
    }

    @Test
    public void getArrayMethodReturnsNullValueWhenKeyIsMissing() {
        assertNull(new MultiValueTable<>().getArray(1));
    }

    @Test
    public void testGetSync() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertEquals(
                entry.getValue(),
                multiValueTable.getSync(entry.getKey())
            );
        }
    }

    @Test
    public void getSyncMethodReturnsVectorObject() {
        List<String> values = Arrays.asList("one", "two");
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, values);

        Object object = multiValueTable.getSync(1);
        assertThat(object, instanceOf(Vector.class));
        assertEquals(new Vector<>(values), object);
    }

    @Test
    public void getSyncMethodReturnsNullIfKeyIsMissing() {
        assertNull(new MultiValueTable<>().getSync("notExistingKey"));
    }

    @Test
    public void testGetIterateAll() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            int i = 0;
            for (Object value : multiValueTable.iterateAll(entry.getKey())) {
                assertEquals(entry.getValue().get(i), value);
                ++i;
            }
        }
    }

    @Test
    public void iterateAllMethodReturnsImmutableIterable() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, Arrays.asList("one", "two"));

        Iterable<Object> iterable = multiValueTable.iterateAll(1);

        multiValueTable.put(1, "three");

        int count = 0;
        for (Object o : iterable) {
            ++count;
        }
        assertEquals(2, count);

        Iterator<Object> iterator = multiValueTable.iterateAll(1).iterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);

        assertEquals(3, multiValueTable.getAllAsList(1).size());
    }

    @Test
    public void countAllMethodReturnsZeroForNonExistentKey() {
        assertEquals(0, multiValueTable.countAll(NON_EXISTING_KEY));
    }

    @Test
    public void testCountAll() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertEquals(entry.getValue().size(), multiValueTable.countAll(entry.getKey()));
        }
    }

    @Test
    public void removeWorksForNonExistingKey() {
        multiValueTable.remove(NON_EXISTING_KEY);
    }

    @Test
    public void testRemove() {
        for (Integer key : sampleObjects.keySet()) {
            multiValueTable.remove(key);
        }
        assertTrue(multiValueTable.isEmpty());
    }

    @Test
    public void isEmptyReturnsTrueForEmptyTable() {
        multiValueTable = new MultiValueTable<>();
        assertTrue(multiValueTable.isEmpty());
    }

    @Test
    public void testIsEmpty() {
        assertFalse(multiValueTable.isEmpty());
        multiValueTable.clear();
        assertTrue(multiValueTable.isEmpty());
    }


    @Test
    public void testClear() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        multiValueTable.clear();
        assertNull(multiValueTable.getFirst(1));
    }

    @Test
    public void removeElementCanBeExecutedForNonExistingKey() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        assertFalse(
            multiValueTable.removeElement(NON_EXISTING_KEY, "one")
        );
        assertEquals("one", multiValueTable.getFirst(1));
    }

    @Test
    public void removeElementCanBeExecutedForNonExistingElement() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        assertFalse(
            multiValueTable.removeElement(1, "two")
        );
        assertEquals("one", multiValueTable.getFirst(1));
    }

    @Test
    public void removeElementCanBeExecutedForEmptyTable() {
        assertFalse(new MultiValueTable<>().removeElement(1, "one"));
    }

    @Test
    public void testRemoveElement() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            for (Object element : entry.getValue()) {
                assertTrue(
                    multiValueTable.removeElement(entry.getKey(), element)
                );
            }
        }
        assertTrue(multiValueTable.isEmpty());
    }

    @Test
    public void removeElementRemovesOnlyFirstElement() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, Arrays.asList("one", "two", "two", "two", "three"));
        assertTrue(multiValueTable.removeElement(1, "two"));
        assertEquals(Arrays.asList("one", "two", "two", "three"), multiValueTable.values());
    }

    @Test
    public void removeElementDoesNotAffectPreviouslyReturnedValues() {
        List<String> initialValues = Arrays.asList("one", "two", "three");
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, initialValues);
        List<Object> values = multiValueTable.getAllAsList(1);

        multiValueTable.removeElement(1, "two");

        assertEquals(initialValues, values);
    }

    @Test
    public void keysMethodReturnsEmptyEnumerationForEmptyTable() {
        multiValueTable = new MultiValueTable<>();
        Enumeration<Integer> keys = multiValueTable.keys();
        assertNotNull(keys);
        assertFalse(keys.hasMoreElements());
    }

    @Test
    public void keysMethodReturnsImmutableEnumeration() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        multiValueTable.put(2, "two");

        Enumeration<Integer> enumeration = multiValueTable.keys();

        multiValueTable.put(3, "three");

        int count = 0;
        while (enumeration.hasMoreElements()) {
            enumeration.nextElement();
            ++count;
        }
        assertEquals(2, count);
    }

    @Test
    public void keySetMethodReturnsEmptyCollectionForEmptyTable() {
        multiValueTable = new MultiValueTable<>();
        Set<Integer> keys = multiValueTable.keySet();
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void testKeys() {
        Enumeration<Integer> keys = multiValueTable.keys();
        assertNotNull(keys);
        while (keys.hasMoreElements()) {
            assertNotNull(
                sampleObjects.remove(keys.nextElement())
            );
        }
        assertTrue(sampleObjects.isEmpty());
    }

    @Test
    public void testKeySet() {
        assertEquals(
            sampleObjects.keySet(),
            multiValueTable.keySet()
        );
    }

    @Test
    public void keySetMethodReturnsImmutableSet() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        multiValueTable.put(2, "two");

        Set<Integer> keys = multiValueTable.keySet();

        multiValueTable.put(3, "three");

        assertEquals(2, keys.size());

        assertThrows(UnsupportedOperationException.class, () -> keys.remove(3));
        assertEquals(3, multiValueTable.keySet().size());
    }

    @Test
    public void testElements() {
        Set<Object> expectedObjects = sampleObjects.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toCollection(HashSet::new));
        Enumeration<Object> elements = multiValueTable.elements();
        assertNotNull(elements);
        while (elements.hasMoreElements()) {
            assertTrue(
                expectedObjects.remove(elements.nextElement())
            );
        }
        assertTrue(expectedObjects.isEmpty());
    }

    @Test
    public void elementsMethodReturnsImmutableEnumeration() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, Arrays.asList("one", "two"));

        Enumeration<Object> enumeration = multiValueTable.elements();

        multiValueTable.put(3, "three");

        int count = 0;
        while (enumeration.hasMoreElements()) {
            enumeration.nextElement();
            ++count;
        }
        assertEquals(2, count);
    }

    @Test
    public void testValues() {
        assertEquals(
            sampleObjects.values().stream().flatMap(List::stream).collect(Collectors.toList()),
            multiValueTable.values()
        );
    }

    @Test
    public void valuesMethodReturnsImmutableCollection() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, Arrays.asList("one", "two"));

        Collection<Object> values = multiValueTable.values();

        multiValueTable.put(3, "three");

        assertEquals(2, values.size());

        assertThrows(UnsupportedOperationException.class, () -> values.remove(1));
        assertEquals(3, multiValueTable.values().size());
    }

    @Test
    public void testEntrySet() {
        Set<Integer> keys = new HashSet<>(sampleObjects.size());
        for (Map.Entry<Integer, List<Object>> entry : multiValueTable.entrySet()) {
            Integer key = entry.getKey();
            assertTrue(sampleObjects.containsKey(key));
            keys.add(key);

            assertEquals(sampleObjects.get(key), entry.getValue());
        }
        assertEquals(sampleObjects.keySet(), keys);
    }

    @Test
    public void entrySetMethodReturnsImmutableCollection() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.putAll(1, Arrays.asList("one", "two"));
        multiValueTable.putAll(2, Arrays.asList("three", "four"));

        Set<Map.Entry<Integer, List<Object>>> entries = multiValueTable.entrySet();

        multiValueTable.put(3, "three");

        assertEquals(2, entries.size());

        assertThrows(UnsupportedOperationException.class, entries::clear);
        for (Map.Entry<Integer, List<Object>> entry : entries) {
            assertThrows(UnsupportedOperationException.class, () -> entry.getValue().remove(0));
        }

        assertEquals(3, multiValueTable.entrySet().size());
        assertEquals(5, multiValueTable.values().size());
    }

    /**
     * Tests elements() and keys() method
     * verifying their behavior when putting the same
     * value for different keys.
     */
    @Test
    public void testDifferentKeysSameElement() {
        int keysCount = 2;
        MultiValueTable<Object, Object> methodMVTable = new MultiValueTable<>();
        String sampleValue = "sampleValue";
        //putting the same value for different keys
        for (int i = 0; i < keysCount; i++) {
            methodMVTable.put(new Object(), sampleValue);
        }

        assertEquals(keysCount, methodMVTable.keySet().size());

        int elementCount = 0;
        for (Object key : methodMVTable.keySet()) {
            elementCount += methodMVTable.countAll(key);
        }
        assertEquals(2, elementCount);
    }

    @Test
    public void invokeToString() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        multiValueTable.put(2, "two");
        multiValueTable.putAll(3, Arrays.asList("three", "tres"));

        String str = multiValueTable.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
        for (Object token : Arrays.asList(
            1, "one",
            2, "two",
            3, "three", "tres"
        )) {
            assertTrue(str.contains(token.toString()));
        }
    }
}
