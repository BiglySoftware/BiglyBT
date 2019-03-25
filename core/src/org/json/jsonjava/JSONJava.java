/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package org.json.jsonjava;

import java.util.*;
import java.math.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.io.*;
import java.lang.reflect.*;
import static java.lang.String.format;

public class JSONJava{

    /**
     * Produce a string from a Number.
     *
     * @param number
     *            A Number
     * @return A String.
     * @throws JSONException
     *             If n is a non-finite number.
     */
    public static String numberToString(Number number) throws JSONException {
        if (number == null) {
            throw new JSONException("Null pointer");
        }
        testValidity(number);

        // Shave off trailing zeros and decimal point, if possible.

        String string = number.toString();
        if (string.indexOf('.') > 0 && string.indexOf('e') < 0
                && string.indexOf('E') < 0) {
            while (string.endsWith("0")) {
                string = string.substring(0, string.length() - 1);
            }
            if (string.endsWith(".")) {
                string = string.substring(0, string.length() - 1);
            }
        }
        return string;
    }

    /**
     * Make a JSON text of an Object value. If the object has an
     * value.toJSONString() method, then that method will be used to produce the
     * JSON text. The method is required to produce a strictly conforming text.
     * If the object does not contain a toJSONString method (which is the most
     * common case), then a text will be produced by other means. If the value
     * is an array or Collection, then a JSONArray will be made from it and its
     * toJSONString method will be called. If the value is a MAP, then a
     * JSONObject will be made from it and its toJSONString method will be
     * called. Otherwise, the value's toString method will be called, and the
     * result will be quoted.
     *
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value
     *            The value to be serialized.
     * @return a printable, displayable, transmittable representation of the
     *         object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws JSONException
     *             If the value is or contains an invalid number.
     */
    public static String valueToString(Object value) throws JSONException {
        if (value == null || value.equals(null)) {
            return "null";
        }
        if (value instanceof JSONString) {
            Object object;
            try {
                object = ((JSONString) value).toJSONString();
            } catch (Exception e) {
                throw new JSONException(e);
            }
            if (object instanceof String) {
                return (String) object;
            }
            throw new JSONException("Bad value from toJSONString: " + object);
        }
        if (value instanceof Number) {
            // not all Numbers may match actual JSON Numbers. i.e. Fractions or Complex
            final String numberAsString = numberToString((Number) value);
            try {
                // Use the BigDecimal constructor for it's parser to validate the format.
                @SuppressWarnings("unused")
                BigDecimal unused = new BigDecimal(numberAsString);
                // Close enough to a JSON number that we will return it unquoted
                return numberAsString;
            } catch (NumberFormatException ex){
                // The Number value is not a valid JSON number.
                // Instead we will quote it as a string
                return quote(numberAsString);
            }
        }
        if (value instanceof Boolean || value instanceof JSONObject
                || value instanceof JSONArray) {
            return value.toString();
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            return new JSONObject(map).toString();
        }
        if (value instanceof Collection) {
            Collection<?> coll = (Collection<?>) value;
            return new JSONArray(coll).toString();
        }
        if (value.getClass().isArray()) {
            return new JSONArray(value).toString();
        }
        if(value instanceof Enum<?>){
            return quote(((Enum<?>)value).name());
        }
        return quote(value.toString());
    }
    
	/**
	 * JSONWriter provides a quick and convenient way of producing JSON text.
	 * The texts produced strictly conform to JSON syntax rules. No whitespace is
	 * added, so the results are ready for transmission or storage. Each instance of
	 * JSONWriter can produce one JSON text.
	 * <p>
	 * A JSONWriter instance provides a <code>value</code> method for appending
	 * values to the
	 * text, and a <code>key</code>
	 * method for adding keys before values in objects. There are <code>array</code>
	 * and <code>endArray</code> methods that make and bound array values, and
	 * <code>object</code> and <code>endObject</code> methods which make and bound
	 * object values. All of these methods return the JSONWriter instance,
	 * permitting a cascade style. For example, <pre>
	 * new JSONWriter(myWriter)
	 *     .object()
	 *         .key("JSON")
	 *         .value("Hello, World!")
	 *     .endObject();</pre> which writes <pre>
	 * {"JSON":"Hello, World!"}</pre>
	 * <p>
	 * The first method called must be <code>array</code> or <code>object</code>.
	 * There are no methods for adding commas or colons. JSONWriter adds them for
	 * you. Objects and arrays can be nested up to 200 levels deep.
	 * <p>
	 * This can sometimes be easier than using a JSONObject to build a string.
	 * @author JSON.org
	 * @version 2016-08-08
	 */
	public class JSONWriter {
	    private static final int maxdepth = 200;

	    /**
	     * The comma flag determines if a comma should be output before the next
	     * value.
	     */
	    private boolean comma;

	    /**
	     * The current mode. Values:
	     * 'a' (array),
	     * 'd' (done),
	     * 'i' (initial),
	     * 'k' (key),
	     * 'o' (object).
	     */
	    protected char mode;

	    /**
	     * The object/array stack.
	     */
	    private final JSONObject stack[];

	    /**
	     * The stack top index. A value of 0 indicates that the stack is empty.
	     */
	    private int top;

	    /**
	     * The writer that will receive the output.
	     */
	    protected Appendable writer;

	    /**
	     * Make a fresh JSONWriter. It can be used to build one JSON text.
	     */
	    public JSONWriter(Appendable w) {
	        this.comma = false;
	        this.mode = 'i';
	        this.stack = new JSONObject[maxdepth];
	        this.top = 0;
	        this.writer = w;
	    }

	    /**
	     * Append a value.
	     * @param string A string value.
	     * @return this
	     * @throws JSONException If the value is out of sequence.
	     */
	    private JSONWriter append(String string) throws JSONException {
	        if (string == null) {
	            throw new JSONException("Null pointer");
	        }
	        if (this.mode == 'o' || this.mode == 'a') {
	            try {
	                if (this.comma && this.mode == 'a') {
	                    this.writer.append(',');
	                }
	                this.writer.append(string);
	            } catch (IOException e) {
	            	// Android as of API 25 does not support this exception constructor
	            	// however we won't worry about it. If an exception is happening here
	            	// it will just throw a "Method not found" exception instead.
	                throw new JSONException(e);
	            }
	            if (this.mode == 'o') {
	                this.mode = 'k';
	            }
	            this.comma = true;
	            return this;
	        }
	        throw new JSONException("Value out of sequence.");
	    }

	    /**
	     * Begin appending a new array. All values until the balancing
	     * <code>endArray</code> will be appended to this array. The
	     * <code>endArray</code> method must be called to mark the array's end.
	     * @return this
	     * @throws JSONException If the nesting is too deep, or if the object is
	     * started in the wrong place (for example as a key or after the end of the
	     * outermost array or object).
	     */
	    public JSONWriter array() throws JSONException {
	        if (this.mode == 'i' || this.mode == 'o' || this.mode == 'a') {
	            this.push(null);
	            this.append("[");
	            this.comma = false;
	            return this;
	        }
	        throw new JSONException("Misplaced array.");
	    }

	    /**
	     * End something.
	     * @param m Mode
	     * @param c Closing character
	     * @return this
	     * @throws JSONException If unbalanced.
	     */
	    private JSONWriter end(char m, char c) throws JSONException {
	        if (this.mode != m) {
	            throw new JSONException(m == 'a'
	                ? "Misplaced endArray."
	                : "Misplaced endObject.");
	        }
	        this.pop(m);
	        try {
	            this.writer.append(c);
	        } catch (IOException e) {
	        	// Android as of API 25 does not support this exception constructor
	        	// however we won't worry about it. If an exception is happening here
	        	// it will just throw a "Method not found" exception instead.
	            throw new JSONException(e);
	        }
	        this.comma = true;
	        return this;
	    }

	    /**
	     * End an array. This method most be called to balance calls to
	     * <code>array</code>.
	     * @return this
	     * @throws JSONException If incorrectly nested.
	     */
	    public JSONWriter endArray() throws JSONException {
	        return this.end('a', ']');
	    }

	    /**
	     * End an object. This method most be called to balance calls to
	     * <code>object</code>.
	     * @return this
	     * @throws JSONException If incorrectly nested.
	     */
	    public JSONWriter endObject() throws JSONException {
	        return this.end('k', '}');
	    }

	    /**
	     * Append a key. The key will be associated with the next value. In an
	     * object, every value must be preceded by a key.
	     * @param string A key string.
	     * @return this
	     * @throws JSONException If the key is out of place. For example, keys
	     *  do not belong in arrays or if the key is null.
	     */
	    public JSONWriter key(String string) throws JSONException {
	        if (string == null) {
	            throw new JSONException("Null key.");
	        }
	        if (this.mode == 'k') {
	            try {
	                JSONObject topObject = this.stack[this.top - 1];
	                // don't use the built in putOnce method to maintain Android support
					if(topObject.has(string)) {
						throw new JSONException("Duplicate key \"" + string + "\"");
					}
	                topObject.put(string, true);
	                if (this.comma) {
	                    this.writer.append(',');
	                }
	                this.writer.append(quote(string));
	                this.writer.append(':');
	                this.comma = false;
	                this.mode = 'o';
	                return this;
	            } catch (IOException e) {
	            	// Android as of API 25 does not support this exception constructor
	            	// however we won't worry about it. If an exception is happening here
	            	// it will just throw a "Method not found" exception instead.
	                throw new JSONException(e);
	            }
	        }
	        throw new JSONException("Misplaced key.");
	    }


	    /**
	     * Begin appending a new object. All keys and values until the balancing
	     * <code>endObject</code> will be appended to this object. The
	     * <code>endObject</code> method must be called to mark the object's end.
	     * @return this
	     * @throws JSONException If the nesting is too deep, or if the object is
	     * started in the wrong place (for example as a key or after the end of the
	     * outermost array or object).
	     */
	    public JSONWriter object() throws JSONException {
	        if (this.mode == 'i') {
	            this.mode = 'o';
	        }
	        if (this.mode == 'o' || this.mode == 'a') {
	            this.append("{");
	            this.push(new JSONObject());
	            this.comma = false;
	            return this;
	        }
	        throw new JSONException("Misplaced object.");

	    }


	    /**
	     * Pop an array or object scope.
	     * @param c The scope to close.
	     * @throws JSONException If nesting is wrong.
	     */
	    private void pop(char c) throws JSONException {
	        if (this.top <= 0) {
	            throw new JSONException("Nesting error.");
	        }
	        char m = this.stack[this.top - 1] == null ? 'a' : 'k';
	        if (m != c) {
	            throw new JSONException("Nesting error.");
	        }
	        this.top -= 1;
	        this.mode = this.top == 0
	            ? 'd'
	            : this.stack[this.top - 1] == null
	            ? 'a'
	            : 'k';
	    }

	    /**
	     * Push an array or object scope.
	     * @param jo The scope to open.
	     * @throws JSONException If nesting is too deep.
	     */
	    private void push(JSONObject jo) throws JSONException {
	        if (this.top >= maxdepth) {
	            throw new JSONException("Nesting too deep.");
	        }
	        this.stack[this.top] = jo;
	        this.mode = jo == null ? 'a' : 'k';
	        this.top += 1;
	    }
	    




	    /**
	     * Append either the value <code>true</code> or the value
	     * <code>false</code>.
	     * @param b A boolean.
	     * @return this
	     * @throws JSONException
	     */
	    public JSONWriter value(boolean b) throws JSONException {
	        return this.append(b ? "true" : "false");
	    }

	    /**
	     * Append a double value.
	     * @param d A double.
	     * @return this
	     * @throws JSONException If the number is not finite.
	     */
	    public JSONWriter value(double d) throws JSONException {
	        return this.value(new Double(d));
	    }

	    /**
	     * Append a long value.
	     * @param l A long.
	     * @return this
	     * @throws JSONException
	     */
	    public JSONWriter value(long l) throws JSONException {
	        return this.append(Long.toString(l));
	    }


	    /**
	     * Append an object value.
	     * @param object The object to append. It can be null, or a Boolean, Number,
	     *   String, JSONObject, or JSONArray, or an object that implements JSONString.
	     * @return this
	     * @throws JSONException If the value is out of sequence.
	     */
	    public JSONWriter value(Object object) throws JSONException {
	        return this.append(valueToString(object));
	    }
	}

	/**
	 * The <code>JSONString</code> interface allows a <code>toJSONString()</code>
	 * method so that a class can change the behavior of
	 * <code>JSONObject.toString()</code>, <code>JSONArray.toString()</code>,
	 * and <code>JSONWriter.value(</code>Object<code>)</code>. The
	 * <code>toJSONString</code> method will be used instead of the default behavior
	 * of using the Object's <code>toString()</code> method and quoting the result.
	 */
	public interface JSONString {
	    /**
	     * The <code>toJSONString</code> method allows a class to produce its own JSON
	     * serialization.
	     *
	     * @return A strictly syntactically correct JSON text.
	     */
	    public String toJSONString();
	}

	/**
	 * The JSONPointerException is thrown by {@link JSONPointer} if an error occurs
	 * during evaluating a pointer.
	 * 
	 * @author JSON.org
	 * @version 2016-05-13
	 */
	public static class JSONPointerException extends JSONException {
	    private static final long serialVersionUID = 8872944667561856751L;

	    public JSONPointerException(String message) {
	        super(message);
	    }

	    public JSONPointerException(String message, Throwable cause) {
	        super(message, cause);
	    }

	}

	/**
	 * A JSON Pointer is a simple query language defined for JSON documents by
	 * <a href="https://tools.ietf.org/html/rfc6901">RFC 6901</a>.
	 * 
	 * In a nutshell, JSONPointer allows the user to navigate into a JSON document
	 * using strings, and retrieve targeted objects, like a simple form of XPATH.
	 * Path segments are separated by the '/' char, which signifies the root of
	 * the document when it appears as the first char of the string. Array 
	 * elements are navigated using ordinals, counting from 0. JSONPointer strings
	 * may be extended to any arbitrary number of segments. If the navigation
	 * is successful, the matched item is returned. A matched item may be a
	 * JSONObject, a JSONArray, or a JSON value. If the JSONPointer string building 
	 * fails, an appropriate exception is thrown. If the navigation fails to find
	 * a match, a JSONPointerException is thrown. 
	 * 
	 * @author JSON.org
	 * @version 2016-05-14
	 */
	public static class JSONPointer {

	    // used for URL encoding and decoding
	    private static final String ENCODING = "utf-8";

	    /**
	     * This class allows the user to build a JSONPointer in steps, using
	     * exactly one segment in each step.
	     */
	    public class Builder {

	        // Segments for the eventual JSONPointer string
	        private final List<String> refTokens = new ArrayList<String>();

	        /**
	         * Creates a {@code JSONPointer} instance using the tokens previously set using the
	         * {@link #append(String)} method calls.
	         */
	        public JSONPointer build() {
	            return new JSONPointer(this.refTokens);
	        }

	        /**
	         * Adds an arbitrary token to the list of reference tokens. It can be any non-null value.
	         * 
	         * Unlike in the case of JSON string or URI fragment representation of JSON pointers, the
	         * argument of this method MUST NOT be escaped. If you want to query the property called
	         * {@code "a~b"} then you should simply pass the {@code "a~b"} string as-is, there is no
	         * need to escape it as {@code "a~0b"}.
	         * 
	         * @param token the new token to be appended to the list
	         * @return {@code this}
	         * @throws NullPointerException if {@code token} is null
	         */
	        public Builder append(String token) {
	            if (token == null) {
	                throw new NullPointerException("token cannot be null");
	            }
	            this.refTokens.add(token);
	            return this;
	        }

	        /**
	         * Adds an integer to the reference token list. Although not necessarily, mostly this token will
	         * denote an array index. 
	         * 
	         * @param arrayIndex the array index to be added to the token list
	         * @return {@code this}
	         */
	        public Builder append(int arrayIndex) {
	            this.refTokens.add(String.valueOf(arrayIndex));
	            return this;
	        }
	    }

	    /**
	     * Static factory method for {@link Builder}. Example usage:
	     * 
	     * <pre><code>
	     * JSONPointer pointer = JSONPointer.builder()
	     *       .append("obj")
	     *       .append("other~key").append("another/key")
	     *       .append("\"")
	     *       .append(0)
	     *       .build();
	     * </code></pre>
	     * 
	     *  @return a builder instance which can be used to construct a {@code JSONPointer} instance by chained
	     *  {@link Builder#append(String)} calls.
	     */
	    public Builder builder() {
	        return new Builder();
	    }

	    // Segments for the JSONPointer string
	    private final List<String> refTokens;

	    /**
	     * Pre-parses and initializes a new {@code JSONPointer} instance. If you want to
	     * evaluate the same JSON Pointer on different JSON documents then it is recommended
	     * to keep the {@code JSONPointer} instances due to performance considerations.
	     * 
	     * @param pointer the JSON String or URI Fragment representation of the JSON pointer.
	     * @throws IllegalArgumentException if {@code pointer} is not a valid JSON pointer
	     */
	    public JSONPointer(final String pointer) {
	        if (pointer == null) {
	            throw new NullPointerException("pointer cannot be null");
	        }
	        if (pointer.isEmpty() || pointer.equals("#")) {
	            this.refTokens = Collections.emptyList();
	            return;
	        }
	        String refs;
	        if (pointer.startsWith("#/")) {
	            refs = pointer.substring(2);
	            try {
	                refs = URLDecoder.decode(refs, ENCODING);
	            } catch (UnsupportedEncodingException e) {
	                throw new RuntimeException(e);
	            }
	        } else if (pointer.startsWith("/")) {
	            refs = pointer.substring(1);
	        } else {
	            throw new IllegalArgumentException("a JSON pointer should start with '/' or '#/'");
	        }
	        this.refTokens = new ArrayList<String>();
	        for (String token : refs.split("/")) {
	            this.refTokens.add(unescape(token));
	        }
	    }

	    public JSONPointer(List<String> refTokens) {
	        this.refTokens = new ArrayList<String>(refTokens);
	    }

	    private String unescape(String token) {
	        return token.replace("~1", "/").replace("~0", "~")
	                .replace("\\\"", "\"")
	                .replace("\\\\", "\\");
	    }

	    /**
	     * Evaluates this JSON Pointer on the given {@code document}. The {@code document}
	     * is usually a {@link JSONObject} or a {@link JSONArray} instance, but the empty
	     * JSON Pointer ({@code ""}) can be evaluated on any JSON values and in such case the
	     * returned value will be {@code document} itself. 
	     * 
	     * @param document the JSON document which should be the subject of querying.
	     * @return the result of the evaluation
	     * @throws JSONPointerException if an error occurs during evaluation
	     */
	    public Object queryFrom(Object document) throws JSONPointerException {
	        if (this.refTokens.isEmpty()) {
	            return document;
	        }
	        Object current = document;
	        for (String token : this.refTokens) {
	            if (current instanceof JSONObject) {
	                current = ((JSONObject) current).opt(unescape(token));
	            } else if (current instanceof JSONArray) {
	                current = readByIndexToken(current, token);
	            } else {
	                throw new JSONPointerException(format(
	                        "value [%s] is not an array or object therefore its key %s cannot be resolved", current,
	                        token));
	            }
	        }
	        return current;
	    }

	    /**
	     * Matches a JSONArray element by ordinal position
	     * @param current the JSONArray to be evaluated
	     * @param indexToken the array index in string form
	     * @return the matched object. If no matching item is found a
	     * @throws JSONPointerException is thrown if the index is out of bounds
	     */
	    private Object readByIndexToken(Object current, String indexToken) throws JSONPointerException {
	        try {
	            int index = Integer.parseInt(indexToken);
	            JSONArray currentArr = (JSONArray) current;
	            if (index >= currentArr.length()) {
	                throw new JSONPointerException(format("index %d is out of bounds - the array has %d elements", index,
	                        currentArr.length()));
	            }
	            try {
					return currentArr.get(index);
				} catch (JSONException e) {
					throw new JSONPointerException("Error reading value at index position " + index, e);
				}
	        } catch (NumberFormatException e) {
	            throw new JSONPointerException(format("%s is not an array index", indexToken), e);
	        }
	    }

	    /**
	     * Returns a string representing the JSONPointer path value using string
	     * representation
	     */
	    @Override
	    public String toString() {
	        StringBuilder rval = new StringBuilder("");
	        for (String token: this.refTokens) {
	            rval.append('/').append(escape(token));
	        }
	        return rval.toString();
	    }

	    /**
	     * Escapes path segment values to an unambiguous form.
	     * The escape char to be inserted is '~'. The chars to be escaped 
	     * are ~, which maps to ~0, and /, which maps to ~1. Backslashes
	     * and double quote chars are also escaped.
	     * @param token the JSONPointer segment value to be escaped
	     * @return the escaped value for the token
	     */
	    private String escape(String token) {
	        return token.replace("~", "~0")
	                .replace("/", "~1")
	                .replace("\\", "\\\\")
	                .replace("\"", "\\\"");
	    }

	    /**
	     * Returns a string representing the JSONPointer path value using URI
	     * fragment identifier representation
	     */
	    public String toURIFragment() {
	        try {
	            StringBuilder rval = new StringBuilder("#");
	            for (String token : this.refTokens) {
	                rval.append('/').append(URLEncoder.encode(token, ENCODING));
	            }
	            return rval.toString();
	        } catch (UnsupportedEncodingException e) {
	            throw new RuntimeException(e);
	        }
	    }
	    
	}

	/**
	 * A JSONObject is an unordered collection of name/value pairs. Its external
	 * form is a string wrapped in curly braces with colons between the names and
	 * values, and commas between the values and names. The internal form is an
	 * object having <code>get</code> and <code>opt</code> methods for accessing
	 * the values by name, and <code>put</code> methods for adding or replacing
	 * values by name. The values can be any of these types: <code>Boolean</code>,
	 * <code>JSONArray</code>, <code>JSONObject</code>, <code>Number</code>,
	 * <code>String</code>, or the <code>NULL</code> object. A
	 * JSONObject constructor can be used to convert an external form JSON text
	 * into an internal form whose values can be retrieved with the
	 * <code>get</code> and <code>opt</code> methods, or to convert values into a
	 * JSON text using the <code>put</code> and <code>toString</code> methods. A
	 * <code>get</code> method returns a value if one can be found, and throws an
	 * exception if one cannot be found. An <code>opt</code> method returns a
	 * default value instead of throwing an exception, and so is useful for
	 * obtaining optional values.
	 * <p>
	 * The generic <code>get()</code> and <code>opt()</code> methods return an
	 * object, which you can cast or query for type. There are also typed
	 * <code>get</code> and <code>opt</code> methods that do type checking and type
	 * coercion for you. The opt methods differ from the get methods in that they
	 * do not throw. Instead, they return a specified value, such as null.
	 * <p>
	 * The <code>put</code> methods add or replace values in an object. For
	 * example,
	 *
	 * <pre>
	 * myString = new JSONObject()
	 *         .put(&quot;JSON&quot;, &quot;Hello, World!&quot;).toString();
	 * </pre>
	 *
	 * produces the string <code>{"JSON": "Hello, World"}</code>.
	 * <p>
	 * The texts produced by the <code>toString</code> methods strictly conform to
	 * the JSON syntax rules. The constructors are more forgiving in the texts they
	 * will accept:
	 * <ul>
	 * <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may appear just
	 * before the closing brace.</li>
	 * <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
	 * quote)</small>.</li>
	 * <li>Strings do not need to be quoted at all if they do not begin with a
	 * quote or single quote, and if they do not contain leading or trailing
	 * spaces, and if they do not contain any of these characters:
	 * <code>{ } [ ] / \ : , #</code> and if they do not look like numbers and
	 * if they are not the reserved words <code>true</code>, <code>false</code>,
	 * or <code>null</code>.</li>
	 * </ul>
	 *
	 * @author JSON.org
	 * @version 2016-08-15
	 */
	
    private static final class Null {

        /**
         * There is only intended to be a single instance of the NULL object,
         * so the clone method returns itself.
         *
         * @return NULL.
         */
        @Override
        protected final Object clone() {
            return this;
        }

        /**
         * A Null object is equal to the null value and to itself.
         *
         * @param object
         *            An object to test for nullness.
         * @return true if the object parameter is the NULL object or
         *         null.
         */
        @Override
        public boolean equals(Object object) {
            return object == null || object == this;
        }
        /**
         * A Null object is equal to the null value and to itself.
         *
         * @return always returns 0.
         */
        @Override
        public int hashCode() {
            return 0;
        }

        /**
         * Get the "null" string value.
         *
         * @return The string "null".
         */
        @Override
        public String toString() {
            return "null";
        }
    }

    public static final Object NULL = new Null();

    /**
     * Tests if the value should be tried as a decimal. It makes no test if there are actual digits.
     * 
     * @param val value to test
     * @return true if the string is "-0" or if it contains '.', 'e', or 'E', false otherwise.
     */
    protected static boolean isDecimalNotation(final String val) {
        return val.indexOf('.') > -1 || val.indexOf('e') > -1
                || val.indexOf('E') > -1 || "-0".equals(val);
    }
    
    protected static Number stringToNumber(final String val) throws NumberFormatException {
        char initial = val.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            // decimal representation
            if (isDecimalNotation(val)) {
                // quick dirty way to see if we need a BigDecimal instead of a Double
                // this only handles some cases of overflow or underflow
                if (val.length()>14) {
                    return new BigDecimal(val);
                }
                final Double d = Double.valueOf(val);
                if (d.isInfinite() || d.isNaN()) {
                    // if we can't parse it as a double, go up to BigDecimal
                    // this is probably due to underflow like 4.32e-678
                    // or overflow like 4.65e5324. The size of the string is small
                    // but can't be held in a Double.
                    return new BigDecimal(val);
                }
                return d;
            }
            // integer representation.
            // This will narrow any values to the smallest reasonable Object representation
            // (Integer, Long, or BigInteger)
            
            // string version
            // The compare string length method reduces GC,
            // but leads to smaller integers being placed in larger wrappers even though not
            // needed. i.e. 1,000,000,000 -> Long even though it's an Integer
            // 1,000,000,000,000,000,000 -> BigInteger even though it's a Long
            //if(val.length()<=9){
            //    return Integer.valueOf(val);
            //}
            //if(val.length()<=18){
            //    return Long.valueOf(val);
            //}
            //return new BigInteger(val);
            
            // BigInteger version: We use a similar bitLenth compare as
            // BigInteger#intValueExact uses. Increases GC, but objects hold
            // only what they need. i.e. Less runtime overhead if the value is
            // long lived. Which is the better tradeoff? This is closer to what's
            // in stringToValue.
            BigInteger bi = new BigInteger(val);
            if(bi.bitLength()<=31){
                return Integer.valueOf(bi.intValue());
            }
            if(bi.bitLength()<=63){
                return Long.valueOf(bi.longValue());
            }
            return bi;
        }
        throw new NumberFormatException("val ["+val+"] is not a valid number.");
    }
    
    /**
     * Produce a string in double quotes with backslash sequences in all the
     * right places. A backslash will be inserted within </, producing <\/,
     * allowing JSON text to be delivered in HTML. In JSON text, a string cannot
     * contain a control character or an unescaped quote or backslash.
     *
     * @param string
     *            A String
     * @return A String correctly formatted for insertion in a JSON text.
     */
    public static String quote(String string) {
        StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {
            try {
                return quote(string, sw).toString();
            } catch (IOException ignored) {
                // will never happen - we are writing to a string writer
                return "";
            }
        }
    }

    public static Writer quote(String string, Writer w) throws IOException {
        if (string == null || string.length() == 0) {
            w.write("\"\"");
            return w;
        }

        char b;
        char c = 0;
        String hhhh;
        int i;
        int len = string.length();

        w.write('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
            case '\\':
            case '"':
                w.write('\\');
                w.write(c);
                break;
            case '/':
                if (b == '<') {
                    w.write('\\');
                }
                w.write(c);
                break;
            case '\b':
                w.write("\\b");
                break;
            case '\t':
                w.write("\\t");
                break;
            case '\n':
                w.write("\\n");
                break;
            case '\f':
                w.write("\\f");
                break;
            case '\r':
                w.write("\\r");
                break;
            default:
                if (c < ' ' || (c >= '\u0080' && c < '\u00a0')
                        || (c >= '\u2000' && c < '\u2100')) {
                    w.write("\\u");
                    hhhh = Integer.toHexString(c);
                    w.write("0000", 0, 4 - hhhh.length());
                    w.write(hhhh);
                } else {
                    w.write(c);
                }
            }
        }
        w.write('"');
        return w;
    }

    
    /**
     * Converts a string to a number using the narrowest possible type. Possible 
     * returns for this function are BigDecimal, Double, BigInteger, Long, and Integer.
     * When a Double is returned, it should always be a valid Double and not NaN or +-infinity.
     * 
     * @param val value to convert
     * @return Number representation of the value.
     * @throws NumberFormatException thrown if the value is not a valid number. A public
     *      caller should catch this and wrap it in a {@link JSONException} if applicable.
     */

    /**
     * Try to convert a string into a number, boolean, or null. If the string
     * can't be converted, return the string.
     *
     * @param string
     *            A String.
     * @return A simple JSON value.
     */
    // Changes to this method must be copied to the corresponding method in
    // the XML class to keep full support for Android
    public static Object stringToValue(String string) {
        if (string.equals("")) {
            return string;
        }
        if (string.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (string.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (string.equalsIgnoreCase("null")) {
            return NULL;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                // if we want full Big Number support this block can be replaced with:
                // return stringToNumber(string);
                if (isDecimalNotation(string)) {
                    Double d = Double.valueOf(string);
                    if (!d.isInfinite() && !d.isNaN()) {
                        return d;
                    }
                } else {
                    Long myLong = Long.valueOf(string);
                    if (string.equals(myLong.toString())) {
                        if (myLong.longValue() == myLong.intValue()) {
                            return Integer.valueOf(myLong.intValue());
                        }
                        return myLong;
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return string;
    }
    
    static final Writer writeValue(Writer writer, Object value,
            int indentFactor, int indent) throws JSONException, IOException {
        if (value == null || value.equals(null)) {
            writer.write("null");
        } else if (value instanceof JSONString) {
            Object o;
            try {
                o = ((JSONString) value).toJSONString();
            } catch (Exception e) {
                throw new JSONException(e);
            }
            writer.write(o != null ? o.toString() : quote(value.toString()));
        } else if (value instanceof Number) {
            // not all Numbers may match actual JSON Numbers. i.e. fractions or Imaginary
            final String numberAsString = numberToString((Number) value);
            try {
                // Use the BigDecimal constructor for it's parser to validate the format.
                @SuppressWarnings("unused")
                BigDecimal testNum = new BigDecimal(numberAsString);
                // Close enough to a JSON number that we will use it unquoted
                writer.write(numberAsString);
            } catch (NumberFormatException ex){
                // The Number value is not a valid JSON number.
                // Instead we will quote it as a string
                quote(numberAsString, writer);
            }
        } else if (value instanceof Boolean) {
            writer.write(value.toString());
        } else if (value instanceof Enum<?>) {
            writer.write(quote(((Enum<?>)value).name()));
        } else if (value instanceof JSONObject) {
            ((JSONObject) value).write(writer, indentFactor, indent);
        } else if (value instanceof JSONArray) {
            ((JSONArray) value).write(writer, indentFactor, indent);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            new JSONObject(map).write(writer, indentFactor, indent);
        } else if (value instanceof Collection) {
            Collection<?> coll = (Collection<?>) value;
            new JSONArray(coll).write(writer, indentFactor, indent);
        } else if (value.getClass().isArray()) {
            new JSONArray(value).write(writer, indentFactor, indent);
        } else {
            quote(value.toString(), writer);
        }
        return writer;
    }

    static final void indent(Writer writer, int indent) throws IOException {
        for (int i = 0; i < indent; i += 1) {
            writer.write(' ');
        }
    }
    
    /**
     * Throw an exception if the object is a NaN or infinite number.
     *
     * @param o
     *            The object to test.
     * @throws JSONException
     *             If o is a non-finite number.
     */
    public static void testValidity(Object o) throws JSONException {
        if (o != null) {
            if (o instanceof Double) {
                if (((Double) o).isInfinite() || ((Double) o).isNaN()) {
                    throw new JSONException(
                            "JSON does not allow non-finite numbers.");
                }
            } else if (o instanceof Float) {
                if (((Float) o).isInfinite() || ((Float) o).isNaN()) {
                    throw new JSONException(
                            "JSON does not allow non-finite numbers.");
                }
            }
        }
    }

    
    /**
     * Make a JSON text of an Object value. If the object has an
     * value.toJSONString() method, then that method will be used to produce the
     * JSON text. The method is required to produce a strictly conforming text.
     * If the object does not contain a toJSONString method (which is the most
     * common case), then a text will be produced by other means. If the value
     * is an array or Collection, then a JSONArray will be made from it and its
     * toJSONString method will be called. If the value is a MAP, then a
     * JSONObject will be made from it and its toJSONString method will be
     * called. Otherwise, the value's toString method will be called, and the
     * result will be quoted.
     *
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value
     *            The value to be serialized.
     * @return a printable, displayable, transmittable representation of the
     *         object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     * @throws JSONException
     *             If the value is or contains an invalid number.
     */

    /**
     * Wrap an object, if necessary. If the object is null, return the NULL
     * object. If it is an array or collection, wrap it in a JSONArray. If it is
     * a map, wrap it in a JSONObject. If it is a standard property (Double,
     * String, et al) then it is already wrapped. Otherwise, if it comes from
     * one of the java packages, turn it into a string. And if it doesn't, try
     * to wrap it in a JSONObject. If the wrapping fails, then null is returned.
     *
     * @param object
     *            The object to wrap
     * @return The wrapped value
     */
    public static Object wrap(Object object) {
        try {
            if (object == null) {
                return NULL;
            }
            if (object instanceof JSONObject || object instanceof JSONArray
                    || NULL.equals(object) || object instanceof JSONString
                    || object instanceof Byte || object instanceof Character
                    || object instanceof Short || object instanceof Integer
                    || object instanceof Long || object instanceof Boolean
                    || object instanceof Float || object instanceof Double
                    || object instanceof String || object instanceof BigInteger
                    || object instanceof BigDecimal || object instanceof Enum) {
                return object;
            }

            if (object instanceof Collection) {
                Collection<?> coll = (Collection<?>) object;
                return new JSONArray(coll);
            }
            if (object.getClass().isArray()) {
                return new JSONArray(object);
            }
            if (object instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) object;
                return new JSONObject(map);
            }
            Package objectPackage = object.getClass().getPackage();
            String objectPackageName = objectPackage != null ? objectPackage
                    .getName() : "";
            if (objectPackageName.startsWith("java.")
                    || objectPackageName.startsWith("javax.")
                    || object.getClass().getClassLoader() == null) {
                return object.toString();
            }
            return new JSONObject(object);
        } catch (Exception exception) {
            return null;
        }
    }
    
	public static class JSONObject {
	    /**
	     * NULL is equivalent to the value that JavaScript calls null,
	     * whilst Java's null is equivalent to the value that JavaScript calls
	     * undefined.
	     */

	    /**
	     * The map where the JSONObject's properties are kept.
	     */
	    private final Map<String, Object> map;

	    /**
	     * It is sometimes more convenient and less ambiguous to have a
	     * <code>NULL</code> object than to use Java's <code>null</code> value.
	     * <code>NULL.equals(null)</code> returns <code>true</code>.
	     * <code>NULL.toString()</code> returns <code>"null"</code>.
	     */

	    /**
	     * Construct an empty JSONObject.
	     */
	    public JSONObject() {
	        // HashMap is used on purpose to ensure that elements are unordered by 
	        // the specification.
	        // JSON tends to be a portable transfer format to allows the container 
	        // implementations to rearrange their items for a faster element 
	        // retrieval based on associative access.
	        // Therefore, an implementation mustn't rely on the order of the item.
	        this.map = new HashMap<String, Object>();
	    }

	    /**
	     * Construct a JSONObject from a subset of another JSONObject. An array of
	     * strings is used to identify the keys that should be copied. Missing keys
	     * are ignored.
	     *
	     * @param jo
	     *            A JSONObject.
	     * @param names
	     *            An array of strings.
	     */
	    public JSONObject(JSONObject jo, String[] names) {
	        this(names.length);
	        for (int i = 0; i < names.length; i += 1) {
	            try {
	                this.putOnce(names[i], jo.opt(names[i]));
	            } catch (Exception ignore) {
	            }
	        }
	    }

	    /**
	     * Construct a JSONObject from a JSONTokener.
	     *
	     * @param x
	     *            A JSONTokener object containing the source string.
	     * @throws JSONException
	     *             If there is a syntax error in the source string or a
	     *             duplicated key.
	     */
	    public JSONObject(JSONTokener x) throws JSONException {
	        this();
	        char c;
	        String key;

	        if (x.nextClean() != '{') {
	            throw x.syntaxError("A JSONObject text must begin with '{'");
	        }
	        for (;;) {
	            c = x.nextClean();
	            switch (c) {
	            case 0:
	                throw x.syntaxError("A JSONObject text must end with '}'");
	            case '}':
	                return;
	            default:
	                x.back();
	                key = x.nextValue().toString();
	            }

	            // The key is followed by ':'.

	            c = x.nextClean();
	            if (c != ':') {
	                throw x.syntaxError("Expected a ':' after a key");
	            }
	            
	            // Use syntaxError(..) to include error location
	            
	            if (key != null) {
	                // Check if key exists
	                if (this.opt(key) != null) {
	                    // key already exists
	                    throw x.syntaxError("Duplicate key \"" + key + "\"");
	                }
	                // Only add value if non-null
	                Object value = x.nextValue();
	                if (value!=null) {
	                    this.put(key, value);
	                }
	            }

	            // Pairs are separated by ','.

	            switch (x.nextClean()) {
	            case ';':
	            case ',':
	                if (x.nextClean() == '}') {
	                    return;
	                }
	                x.back();
	                break;
	            case '}':
	                return;
	            default:
	                throw x.syntaxError("Expected a ',' or '}'");
	            }
	        }
	    }

	    /**
	     * Construct a JSONObject from a Map.
	     *
	     * @param m
	     *            A map object that can be used to initialize the contents of
	     *            the JSONObject.
	     */
	    public JSONObject(Map<?, ?> m) {
	        if (m == null) {
	            this.map = new HashMap<String, Object>();
	        } else {
	            this.map = new HashMap<String, Object>(m.size());
	        	for (final HashMap.Entry<?, ?> e : m.entrySet()) {
	                final Object value = e.getValue();
	                if (value != null) {
	                    this.map.put(String.valueOf(e.getKey()), wrap(value));
	                }
	            }
	        }
	    }

	    /**
	     * Construct a JSONObject from an Object using bean getters. It reflects on
	     * all of the public methods of the object. For each of the methods with no
	     * parameters and a name starting with <code>"get"</code> or
	     * <code>"is"</code> followed by an uppercase letter, the method is invoked,
	     * and a key and the value returned from the getter method are put into the
	     * new JSONObject.
	     * <p>
	     * The key is formed by removing the <code>"get"</code> or <code>"is"</code>
	     * prefix. If the second remaining character is not upper case, then the
	     * first character is converted to lower case.
	     * <p>
	     * For example, if an object has a method named <code>"getName"</code>, and
	     * if the result of calling <code>object.getName()</code> is
	     * <code>"Larry Fine"</code>, then the JSONObject will contain
	     * <code>"name": "Larry Fine"</code>.
	     * <p>
	     * Methods that return <code>void</code> as well as <code>static</code>
	     * methods are ignored.
	     * 
	     * @param bean
	     *            An object that has getter methods that should be used to make
	     *            a JSONObject.
	     */
	    public JSONObject(Object bean) {
	        this();
	        this.populateMap(bean);
	    }

	    /**
	     * Construct a JSONObject from an Object, using reflection to find the
	     * public members. The resulting JSONObject's keys will be the strings from
	     * the names array, and the values will be the field values associated with
	     * those keys in the object. If a key is not found or not visible, then it
	     * will not be copied into the new JSONObject.
	     *
	     * @param object
	     *            An object that has fields that should be used to make a
	     *            JSONObject.
	     * @param names
	     *            An array of strings, the names of the fields to be obtained
	     *            from the object.
	     */
	    public JSONObject(Object object, String names[]) {
	        this(names.length);
	        Class<?> c = object.getClass();
	        for (int i = 0; i < names.length; i += 1) {
	            String name = names[i];
	            try {
	                this.putOpt(name, c.getField(name).get(object));
	            } catch (Exception ignore) {
	            }
	        }
	    }

	    /**
	     * Construct a JSONObject from a source JSON text string. This is the most
	     * commonly used JSONObject constructor.
	     *
	     * @param source
	     *            A string beginning with <code>{</code>&nbsp;<small>(left
	     *            brace)</small> and ending with <code>}</code>
	     *            &nbsp;<small>(right brace)</small>.
	     * @exception JSONException
	     *                If there is a syntax error in the source string or a
	     *                duplicated key.
	     */
	    public JSONObject(String source) throws JSONException {
	        this(new JSONTokener(source));
	    }

	    /**
	     * Construct a JSONObject from a ResourceBundle.
	     *
	     * @param baseName
	     *            The ResourceBundle base name.
	     * @param locale
	     *            The Locale to load the ResourceBundle for.
	     * @throws JSONException
	     *             If any JSONExceptions are detected.
	     */
	    public JSONObject(String baseName, Locale locale) throws JSONException {
	        this();
	        ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale,
	                Thread.currentThread().getContextClassLoader());

	// Iterate through the keys in the bundle.

	        Enumeration<String> keys = bundle.getKeys();
	        while (keys.hasMoreElements()) {
	            Object key = keys.nextElement();
	            if (key != null) {

	// Go through the path, ensuring that there is a nested JSONObject for each
	// segment except the last. Add the value using the last segment's name into
	// the deepest nested JSONObject.

	                String[] path = ((String) key).split("\\.");
	                int last = path.length - 1;
	                JSONObject target = this;
	                for (int i = 0; i < last; i += 1) {
	                    String segment = path[i];
	                    JSONObject nextTarget = target.optJSONObject(segment);
	                    if (nextTarget == null) {
	                        nextTarget = new JSONObject();
	                        target.put(segment, nextTarget);
	                    }
	                    target = nextTarget;
	                }
	                target.put(path[last], bundle.getString((String) key));
	            }
	        }
	    }
	    
	    /**
	     * Constructor to specify an initial capacity of the internal map. Useful for library 
	     * internal calls where we know, or at least can best guess, how big this JSONObject
	     * will be.
	     * 
	     * @param initialCapacity initial capacity of the internal map.
	     */
	    protected JSONObject(int initialCapacity){
	        this.map = new HashMap<String, Object>(initialCapacity);
	    }

	    /**
	     * Accumulate values under a key. It is similar to the put method except
	     * that if there is already an object stored under the key then a JSONArray
	     * is stored under the key to hold all of the accumulated values. If there
	     * is already a JSONArray, then the new value is appended to it. In
	     * contrast, the put method replaces the previous value.
	     *
	     * If only one value is accumulated that is not a JSONArray, then the result
	     * will be the same as using put. But if multiple values are accumulated,
	     * then the result will be like append.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            An object to be accumulated under the key.
	     * @return this.
	     * @throws JSONException
	     *             If the value is an invalid number or if the key is null.
	     */
	    public JSONObject accumulate(String key, Object value) throws JSONException {
	        testValidity(value);
	        Object object = this.opt(key);
	        if (object == null) {
	            this.put(key,
	                    value instanceof JSONArray ? new JSONArray().put(value)
	                            : value);
	        } else if (object instanceof JSONArray) {
	            ((JSONArray) object).put(value);
	        } else {
	            this.put(key, new JSONArray().put(object).put(value));
	        }
	        return this;
	    }

	    /**
	     * Append values to the array under a key. If the key does not exist in the
	     * JSONObject, then the key is put in the JSONObject with its value being a
	     * JSONArray containing the value parameter. If the key was already
	     * associated with a JSONArray, then the value parameter is appended to it.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            An object to be accumulated under the key.
	     * @return this.
	     * @throws JSONException
	     *             If the key is null or if the current value associated with
	     *             the key is not a JSONArray.
	     */
	    public JSONObject append(String key, Object value) throws JSONException {
	        testValidity(value);
	        Object object = this.opt(key);
	        if (object == null) {
	            this.put(key, new JSONArray().put(value));
	        } else if (object instanceof JSONArray) {
	            this.put(key, ((JSONArray) object).put(value));
	        } else {
	            throw new JSONException("JSONObject[" + key
	                    + "] is not a JSONArray.");
	        }
	        return this;
	    }

	    /**
	     * Produce a string from a double. The string "null" will be returned if the
	     * number is not finite.
	     *
	     * @param d
	     *            A double.
	     * @return A String.
	     */
	    public String doubleToString(double d) {
	        if (Double.isInfinite(d) || Double.isNaN(d)) {
	            return "null";
	        }

	// Shave off trailing zeros and decimal point, if possible.

	        String string = Double.toString(d);
	        if (string.indexOf('.') > 0 && string.indexOf('e') < 0
	                && string.indexOf('E') < 0) {
	            while (string.endsWith("0")) {
	                string = string.substring(0, string.length() - 1);
	            }
	            if (string.endsWith(".")) {
	                string = string.substring(0, string.length() - 1);
	            }
	        }
	        return string;
	    }

	    /**
	     * Get the value object associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The object associated with the key.
	     * @throws JSONException
	     *             if the key is not found.
	     */
	    public Object get(String key) throws JSONException {
	        if (key == null) {
	            throw new JSONException("Null key.");
	        }
	        Object object = this.opt(key);
	        if (object == null) {
	            throw new JSONException("JSONObject[" + quote(key) + "] not found.");
	        }
	        return object;
	    }

	    /**
	    * Get the enum value associated with a key.
	    * 
	    * @param clazz
	    *           The type of enum to retrieve.
	    * @param key
	    *           A key string.
	    * @return The enum value associated with the key
	    * @throws JSONException
	    *             if the key is not found or if the value cannot be converted
	    *             to an enum.
	    */
	    public <E extends Enum<E>> E getEnum(Class<E> clazz, String key) throws JSONException {
	        E val = optEnum(clazz, key);
	        if(val==null) {
	            // JSONException should really take a throwable argument.
	            // If it did, I would re-implement this with the Enum.valueOf
	            // method and place any thrown exception in the JSONException
	            throw new JSONException("JSONObject[" + quote(key)
	                    + "] is not an enum of type " + quote(clazz.getSimpleName())
	                    + ".");
	        }
	        return val;
	    }

	    /**
	     * Get the boolean value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The truth.
	     * @throws JSONException
	     *             if the value is not a Boolean or the String "true" or
	     *             "false".
	     */
	    public boolean getBoolean(String key) throws JSONException {
	        Object object = this.get(key);
	        if (object.equals(Boolean.FALSE)
	                || (object instanceof String && ((String) object)
	                        .equalsIgnoreCase("false"))) {
	            return false;
	        } else if (object.equals(Boolean.TRUE)
	                || (object instanceof String && ((String) object)
	                        .equalsIgnoreCase("true"))) {
	            return true;
	        }
	        throw new JSONException("JSONObject[" + quote(key)
	                + "] is not a Boolean.");
	    }

	    /**
	     * Get the BigInteger value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The numeric value.
	     * @throws JSONException
	     *             if the key is not found or if the value cannot 
	     *             be converted to BigInteger.
	     */
	    public BigInteger getBigInteger(String key) throws JSONException {
	        Object object = this.get(key);
	        try {
	            return new BigInteger(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONObject[" + quote(key)
	                    + "] could not be converted to BigInteger.", e);
	        }
	    }

	    /**
	     * Get the BigDecimal value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The numeric value.
	     * @throws JSONException
	     *             if the key is not found or if the value
	     *             cannot be converted to BigDecimal.
	     */
	    public BigDecimal getBigDecimal(String key) throws JSONException {
	        Object object = this.get(key);
	        if (object instanceof BigDecimal) {
	            return (BigDecimal)object;
	        }
	        try {
	            return new BigDecimal(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONObject[" + quote(key)
	                    + "] could not be converted to BigDecimal.", e);
	        }
	    }

	    /**
	     * Get the double value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The numeric value.
	     * @throws JSONException
	     *             if the key is not found or if the value is not a Number
	     *             object and cannot be converted to a number.
	     */
	    public double getDouble(String key) throws JSONException {
	        Object object = this.get(key);
	        try {
	            return object instanceof Number ? ((Number) object).doubleValue()
	                    : Double.parseDouble(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONObject[" + quote(key)
	                    + "] is not a number.", e);
	        }
	    }

	    /**
	     * Get the float value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The numeric value.
	     * @throws JSONException
	     *             if the key is not found or if the value is not a Number
	     *             object and cannot be converted to a number.
	     */
	    public float getFloat(String key) throws JSONException {
	        Object object = this.get(key);
	        try {
	            return object instanceof Number ? ((Number) object).floatValue()
	                    : Float.parseFloat(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONObject[" + quote(key)
	                    + "] is not a number.", e);
	        }
	    }

	    /**
	     * Get the Number value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The numeric value.
	     * @throws JSONException
	     *             if the key is not found or if the value is not a Number
	     *             object and cannot be converted to a number.
	     */
	    public Number getNumber(String key) throws JSONException {
	        Object object = this.get(key);
	        try {
	            if (object instanceof Number) {
	                return (Number)object;
	            }
	            return stringToNumber(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONObject[" + quote(key)
	                    + "] is not a number.", e);
	        }
	    }

	    /**
	     * Get the int value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The integer value.
	     * @throws JSONException
	     *             if the key is not found or if the value cannot be converted
	     *             to an integer.
	     */
	    public int getInt(String key) throws JSONException {
	        Object object = this.get(key);
	        try {
	            return object instanceof Number ? ((Number) object).intValue()
	                    : Integer.parseInt((String) object);
	        } catch (Exception e) {
	            throw new JSONException("JSONObject[" + quote(key)
	                    + "] is not an int.", e);
	        }
	    }

	    /**
	     * Get the JSONArray value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return A JSONArray which is the value.
	     * @throws JSONException
	     *             if the key is not found or if the value is not a JSONArray.
	     */
	    public JSONArray getJSONArray(String key) throws JSONException {
	        Object object = this.get(key);
	        if (object instanceof JSONArray) {
	            return (JSONArray) object;
	        }
	        throw new JSONException("JSONObject[" + quote(key)
	                + "] is not a JSONArray.");
	    }

	    /**
	     * Get the JSONObject value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return A JSONObject which is the value.
	     * @throws JSONException
	     *             if the key is not found or if the value is not a JSONObject.
	     */
	    public JSONObject getJSONObject(String key) throws JSONException {
	        Object object = this.get(key);
	        if (object instanceof JSONObject) {
	            return (JSONObject) object;
	        }
	        throw new JSONException("JSONObject[" + quote(key)
	                + "] is not a JSONObject.");
	    }

	    /**
	     * Get the long value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return The long value.
	     * @throws JSONException
	     *             if the key is not found or if the value cannot be converted
	     *             to a long.
	     */
	    public long getLong(String key) throws JSONException {
	        Object object = this.get(key);
	        try {
	            return object instanceof Number ? ((Number) object).longValue()
	                    : Long.parseLong((String) object);
	        } catch (Exception e) {
	            throw new JSONException("JSONObject[" + quote(key)
	                    + "] is not a long.", e);
	        }
	    }

	    /**
	     * Get an array of field names from a JSONObject.
	     *
	     * @return An array of field names, or null if there are no names.
	     */
	    public String[] getNames(JSONObject jo) {
	        int length = jo.length();
	        if (length == 0) {
	            return null;
	        }
	        return jo.keySet().toArray(new String[length]);
	    }

	    /**
	     * Get an array of field names from an Object.
	     *
	     * @return An array of field names, or null if there are no names.
	     */
	    public String[] getNames(Object object) {
	        if (object == null) {
	            return null;
	        }
	        Class<?> klass = object.getClass();
	        Field[] fields = klass.getFields();
	        int length = fields.length;
	        if (length == 0) {
	            return null;
	        }
	        String[] names = new String[length];
	        for (int i = 0; i < length; i += 1) {
	            names[i] = fields[i].getName();
	        }
	        return names;
	    }

	    /**
	     * Get the string associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return A string which is the value.
	     * @throws JSONException
	     *             if there is no string value for the key.
	     */
	    public String getString(String key) throws JSONException {
	        Object object = this.get(key);
	        if (object instanceof String) {
	            return (String) object;
	        }
	        throw new JSONException("JSONObject[" + quote(key) + "] not a string.");
	    }

	    /**
	     * Determine if the JSONObject contains a specific key.
	     *
	     * @param key
	     *            A key string.
	     * @return true if the key exists in the JSONObject.
	     */
	    public boolean has(String key) {
	        return this.map.containsKey(key);
	    }

	    /**
	     * Increment a property of a JSONObject. If there is no such property,
	     * create one with a value of 1. If there is such a property, and if it is
	     * an Integer, Long, Double, or Float, then add one to it.
	     *
	     * @param key
	     *            A key string.
	     * @return this.
	     * @throws JSONException
	     *             If there is already a property with this name that is not an
	     *             Integer, Long, Double, or Float.
	     */
	    public JSONObject increment(String key) throws JSONException {
	        Object value = this.opt(key);
	        if (value == null) {
	            this.put(key, 1);
	        } else if (value instanceof BigInteger) {
	            this.put(key, ((BigInteger)value).add(BigInteger.ONE));
	        } else if (value instanceof BigDecimal) {
	            this.put(key, ((BigDecimal)value).add(BigDecimal.ONE));
	        } else if (value instanceof Integer) {
	            this.put(key, ((Integer) value).intValue() + 1);
	        } else if (value instanceof Long) {
	            this.put(key, ((Long) value).longValue() + 1L);
	        } else if (value instanceof Double) {
	            this.put(key, ((Double) value).doubleValue() + 1.0d);
	        } else if (value instanceof Float) {
	            this.put(key, ((Float) value).floatValue() + 1.0f);
	        } else {
	            throw new JSONException("Unable to increment [" + quote(key) + "].");
	        }
	        return this;
	    }

	    /**
	     * Determine if the value associated with the key is null or if there is no
	     * value.
	     *
	     * @param key
	     *            A key string.
	     * @return true if there is no value associated with the key or if the value
	     *         is the NULL object.
	     */
	    public boolean isNull(String key) {
	        return NULL.equals(this.opt(key));
	    }

	    /**
	     * Get an enumeration of the keys of the JSONObject. Modifying this key Set will also
	     * modify the JSONObject. Use with caution.
	     *
	     * @see Set#iterator()
	     * 
	     * @return An iterator of the keys.
	     */
	    public Iterator<String> keys() {
	        return this.keySet().iterator();
	    }

	    /**
	     * Get a set of keys of the JSONObject. Modifying this key Set will also modify the
	     * JSONObject. Use with caution.
	     *
	     * @see Map#keySet()
	     *
	     * @return A keySet.
	     */
	    public Set<String> keySet() {
	        return this.map.keySet();
	    }

	    /**
	     * Get a set of entries of the JSONObject. These are raw values and may not
	     * match what is returned by the JSONObject get* and opt* functions. Modifying 
	     * the returned EntrySet or the Entry objects contained therein will modify the
	     * backing JSONObject. This does not return a clone or a read-only view.
	     * 
	     * Use with caution.
	     *
	     * @see Map#entrySet()
	     *
	     * @return An Entry Set
	     */
	    protected Set<HashMap.Entry<String, Object>> entrySet() {
	        return this.map.entrySet();
	    }

	    /**
	     * Get the number of keys stored in the JSONObject.
	     *
	     * @return The number of keys in the JSONObject.
	     */
	    public int length() {
	        return this.map.size();
	    }

	    /**
	     * Produce a JSONArray containing the names of the elements of this
	     * JSONObject.
	     *
	     * @return A JSONArray containing the key strings, or null if the JSONObject
	     *         is empty.
	     */
	    public JSONArray names() {
	    	if(this.map.isEmpty()) {
	    		return null;
	    	}
	        return new JSONArray(this.map.keySet());
	    }


	    /**
	     * Get an optional value associated with a key.
	     *
	     * @param key
	     *            A key string.
	     * @return An object which is the value, or null if there is no value.
	     */
	    public Object opt(String key) {
	        return key == null ? null : this.map.get(key);
	    }

	    /**
	     * Get the enum value associated with a key.
	     * 
	     * @param clazz
	     *            The type of enum to retrieve.
	     * @param key
	     *            A key string.
	     * @return The enum value associated with the key or null if not found
	     */
	    public <E extends Enum<E>> E optEnum(Class<E> clazz, String key) {
	        return this.optEnum(clazz, key, null);
	    }

	    /**
	     * Get the enum value associated with a key.
	     * 
	     * @param clazz
	     *            The type of enum to retrieve.
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default in case the value is not found
	     * @return The enum value associated with the key or defaultValue
	     *            if the value is not found or cannot be assigned to <code>clazz</code>
	     */
	    public <E extends Enum<E>> E optEnum(Class<E> clazz, String key, E defaultValue) {
	        try {
	            Object val = this.opt(key);
	            if (NULL.equals(val)) {
	                return defaultValue;
	            }
	            if (clazz.isAssignableFrom(val.getClass())) {
	                // we just checked it!
	                @SuppressWarnings("unchecked")
	                E myE = (E) val;
	                return myE;
	            }
	            return Enum.valueOf(clazz, val.toString());
	        } catch (IllegalArgumentException e) {
	            return defaultValue;
	        } catch (NullPointerException e) {
	            return defaultValue;
	        }
	    }

	    /**
	     * Get an optional boolean associated with a key. It returns false if there
	     * is no such key, or if the value is not Boolean.TRUE or the String "true".
	     *
	     * @param key
	     *            A key string.
	     * @return The truth.
	     */
	    public boolean optBoolean(String key) {
	        return this.optBoolean(key, false);
	    }

	    /**
	     * Get an optional boolean associated with a key. It returns the
	     * defaultValue if there is no such key, or if it is not a Boolean or the
	     * String "true" or "false" (case insensitive).
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default.
	     * @return The truth.
	     */
	    public boolean optBoolean(String key, boolean defaultValue) {
	        Object val = this.opt(key);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Boolean){
	            return ((Boolean) val).booleanValue();
	        }
	        try {
	            // we'll use the get anyway because it does string conversion.
	            return this.getBoolean(key);
	        } catch (Exception e) {
	            return defaultValue;
	        }
	    }

	    /**
	     * Get an optional BigDecimal associated with a key, or the defaultValue if
	     * there is no such key or if its value is not a number. If the value is a
	     * string, an attempt will be made to evaluate it as a number.
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default.
	     * @return An object which is the value.
	     */
	    public BigDecimal optBigDecimal(String key, BigDecimal defaultValue) {
	        Object val = this.opt(key);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof BigDecimal){
	            return (BigDecimal) val;
	        }
	        if (val instanceof BigInteger){
	            return new BigDecimal((BigInteger) val);
	        }
	        if (val instanceof Double || val instanceof Float){
	            return new BigDecimal(((Number) val).doubleValue());
	        }
	        if (val instanceof Long || val instanceof Integer
	                || val instanceof Short || val instanceof Byte){
	            return new BigDecimal(((Number) val).longValue());
	        }
	        // don't check if it's a string in case of unchecked Number subclasses
	        try {
	            return new BigDecimal(val.toString());
	        } catch (Exception e) {
	            return defaultValue;
	        }
	    }

	    /**
	     * Get an optional BigInteger associated with a key, or the defaultValue if
	     * there is no such key or if its value is not a number. If the value is a
	     * string, an attempt will be made to evaluate it as a number.
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default.
	     * @return An object which is the value.
	     */
	    public BigInteger optBigInteger(String key, BigInteger defaultValue) {
	        Object val = this.opt(key);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof BigInteger){
	            return (BigInteger) val;
	        }
	        if (val instanceof BigDecimal){
	            return ((BigDecimal) val).toBigInteger();
	        }
	        if (val instanceof Double || val instanceof Float){
	            return new BigDecimal(((Number) val).doubleValue()).toBigInteger();
	        }
	        if (val instanceof Long || val instanceof Integer
	                || val instanceof Short || val instanceof Byte){
	            return BigInteger.valueOf(((Number) val).longValue());
	        }
	        // don't check if it's a string in case of unchecked Number subclasses
	        try {
	            // the other opt functions handle implicit conversions, i.e. 
	            // jo.put("double",1.1d);
	            // jo.optInt("double"); -- will return 1, not an error
	            // this conversion to BigDecimal then to BigInteger is to maintain
	            // that type cast support that may truncate the decimal.
	            final String valStr = val.toString();
	            if(isDecimalNotation(valStr)) {
	                return new BigDecimal(valStr).toBigInteger();
	            }
	            return new BigInteger(valStr);
	        } catch (Exception e) {
	            return defaultValue;
	        }
	    }

	    /**
	     * Get an optional double associated with a key, or NaN if there is no such
	     * key or if its value is not a number. If the value is a string, an attempt
	     * will be made to evaluate it as a number.
	     *
	     * @param key
	     *            A string which is the key.
	     * @return An object which is the value.
	     */
	    public double optDouble(String key) {
	        return this.optDouble(key, Double.NaN);
	    }

	    /**
	     * Get an optional double associated with a key, or the defaultValue if
	     * there is no such key or if its value is not a number. If the value is a
	     * string, an attempt will be made to evaluate it as a number.
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default.
	     * @return An object which is the value.
	     */
	    public double optDouble(String key, double defaultValue) {
	        Object val = this.opt(key);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return ((Number) val).doubleValue();
	        }
	        if (val instanceof String) {
	            try {
	                return Double.parseDouble((String) val);
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }

	    /**
	     * Get the optional double value associated with an index. NaN is returned
	     * if there is no value for the index, or if the value is not a number and
	     * cannot be converted to a number.
	     *
	     * @param key
	     *            A key string.
	     * @return The value.
	     */
	    public float optFloat(String key) {
	        return this.optFloat(key, Float.NaN);
	    }

	    /**
	     * Get the optional double value associated with an index. The defaultValue
	     * is returned if there is no value for the index, or if the value is not a
	     * number and cannot be converted to a number.
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default value.
	     * @return The value.
	     */
	    public float optFloat(String key, float defaultValue) {
	        Object val = this.opt(key);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return ((Number) val).floatValue();
	        }
	        if (val instanceof String) {
	            try {
	                return Float.parseFloat((String) val);
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }

	    /**
	     * Get an optional int value associated with a key, or zero if there is no
	     * such key or if the value is not a number. If the value is a string, an
	     * attempt will be made to evaluate it as a number.
	     *
	     * @param key
	     *            A key string.
	     * @return An object which is the value.
	     */
	    public int optInt(String key) {
	        return this.optInt(key, 0);
	    }

	    /**
	     * Get an optional int value associated with a key, or the default if there
	     * is no such key or if the value is not a number. If the value is a string,
	     * an attempt will be made to evaluate it as a number.
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default.
	     * @return An object which is the value.
	     */
	    public int optInt(String key, int defaultValue) {
	        Object val = this.opt(key);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return ((Number) val).intValue();
	        }
	        
	        if (val instanceof String) {
	            try {
	                return new BigDecimal((String) val).intValue();
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }

	    /**
	     * Get an optional JSONArray associated with a key. It returns null if there
	     * is no such key, or if its value is not a JSONArray.
	     *
	     * @param key
	     *            A key string.
	     * @return A JSONArray which is the value.
	     */
	    public JSONArray optJSONArray(String key) {
	        Object o = this.opt(key);
	        return o instanceof JSONArray ? (JSONArray) o : null;
	    }

	    /**
	     * Get an optional JSONObject associated with a key. It returns null if
	     * there is no such key, or if its value is not a JSONObject.
	     *
	     * @param key
	     *            A key string.
	     * @return A JSONObject which is the value.
	     */
	    public JSONObject optJSONObject(String key) {
	        Object object = this.opt(key);
	        return object instanceof JSONObject ? (JSONObject) object : null;
	    }

	    /**
	     * Get an optional long value associated with a key, or zero if there is no
	     * such key or if the value is not a number. If the value is a string, an
	     * attempt will be made to evaluate it as a number.
	     *
	     * @param key
	     *            A key string.
	     * @return An object which is the value.
	     */
	    public long optLong(String key) {
	        return this.optLong(key, 0);
	    }

	    /**
	     * Get an optional long value associated with a key, or the default if there
	     * is no such key or if the value is not a number. If the value is a string,
	     * an attempt will be made to evaluate it as a number.
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default.
	     * @return An object which is the value.
	     */
	    public long optLong(String key, long defaultValue) {
	        Object val = this.opt(key);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return ((Number) val).longValue();
	        }
	        
	        if (val instanceof String) {
	            try {
	                return new BigDecimal((String) val).longValue();
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }
	    
	    /**
	     * Get an optional {@link Number} value associated with a key, or <code>null</code>
	     * if there is no such key or if the value is not a number. If the value is a string,
	     * an attempt will be made to evaluate it as a number ({@link BigDecimal}). This method
	     * would be used in cases where type coercion of the number value is unwanted.
	     *
	     * @param key
	     *            A key string.
	     * @return An object which is the value.
	     */
	    public Number optNumber(String key) {
	        return this.optNumber(key, null);
	    }

	    /**
	     * Get an optional {@link Number} value associated with a key, or the default if there
	     * is no such key or if the value is not a number. If the value is a string,
	     * an attempt will be made to evaluate it as a number. This method
	     * would be used in cases where type coercion of the number value is unwanted.
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default.
	     * @return An object which is the value.
	     */
	    public Number optNumber(String key, Number defaultValue) {
	        Object val = this.opt(key);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return (Number) val;
	        }
	        
	        if (val instanceof String) {
	            try {
	                return stringToNumber((String) val);
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }
	    
	    /**
	     * Get an optional string associated with a key. It returns an empty string
	     * if there is no such key. If the value is not a string and is not null,
	     * then it is converted to a string.
	     *
	     * @param key
	     *            A key string.
	     * @return A string which is the value.
	     */
	    public String optString(String key) {
	        return this.optString(key, "");
	    }

	    /**
	     * Get an optional string associated with a key. It returns the defaultValue
	     * if there is no such key.
	     *
	     * @param key
	     *            A key string.
	     * @param defaultValue
	     *            The default.
	     * @return A string which is the value.
	     */
	    public String optString(String key, String defaultValue) {
	        Object object = this.opt(key);
	        return NULL.equals(object) ? defaultValue : object.toString();
	    }

	    /**
	     * Populates the internal map of the JSONObject with the bean properties.
	     * The bean can not be recursive.
	     *
	     * @see JSONObject#JSONObject(Object)
	     *
	     * @param bean
	     *            the bean
	     */
	    private void populateMap(Object bean) {
	        Class<?> klass = bean.getClass();

	// If klass is a System class then set includeSuperClass to false.

	        boolean includeSuperClass = klass.getClassLoader() != null;

	        Method[] methods = includeSuperClass ? klass.getMethods() : klass
	                .getDeclaredMethods();
	        for (final Method method : methods) {
	            final int modifiers = method.getModifiers();
	            if (Modifier.isPublic(modifiers)
	                    && !Modifier.isStatic(modifiers)
	                    && method.getParameterTypes().length == 0
	                    && !method.isBridge()
	                    && method.getReturnType() != Void.TYPE ) {
	                final String name = method.getName();
	                String key;
	                if (name.startsWith("get")) {
	                    if ("getClass".equals(name) || "getDeclaringClass".equals(name)) {
	                        continue;
	                    }
	                    key = name.substring(3);
	                } else if (name.startsWith("is")) {
	                    key = name.substring(2);
	                } else {
	                    continue;
	                }
	                if (key.length() > 0
	                        && Character.isUpperCase(key.charAt(0))) {
	                    if (key.length() == 1) {
	                        key = key.toLowerCase(Locale.ROOT);
	                    } else if (!Character.isUpperCase(key.charAt(1))) {
	                        key = key.substring(0, 1).toLowerCase(Locale.ROOT)
	                                + key.substring(1);
	                    }

	                    try {
	                        final Object result = method.invoke(bean);
	                        if (result != null) {
	                            this.map.put(key, wrap(result));
	                            // we don't use the result anywhere outside of wrap
	                            // if it's a resource we should be sure to close it after calling toString
	                            if(result instanceof Closeable) {
	                                try {
	                                    ((Closeable)result).close();
	                                } catch (IOException ignore) {
	                                }
	                            }
	                        }
	                    } catch (IllegalAccessException ignore) {
	                    } catch (IllegalArgumentException ignore) {
	                    } catch (InvocationTargetException ignore) {
	                    }
	                }
	            }
	        }
	    }

	    /**
	     * Put a key/boolean pair in the JSONObject.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            A boolean which is the value.
	     * @return this.
	     * @throws JSONException
	     *             If the key is null.
	     */
	    public JSONObject put(String key, boolean value) throws JSONException {
	        this.put(key, value ? Boolean.TRUE : Boolean.FALSE);
	        return this;
	    }

	    /**
	     * Put a key/value pair in the JSONObject, where the value will be a
	     * JSONArray which is produced from a Collection.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            A Collection value.
	     * @return this.
	     * @throws JSONException
	     */
	    public JSONObject put(String key, Collection<?> value) throws JSONException {
	        this.put(key, new JSONArray(value));
	        return this;
	    }

	    /**
	     * Put a key/double pair in the JSONObject.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            A double which is the value.
	     * @return this.
	     * @throws JSONException
	     *             If the key is null or if the number is invalid.
	     */
	    public JSONObject put(String key, double value) throws JSONException {
	        this.put(key, Double.valueOf(value));
	        return this;
	    }
	    
	    /**
	     * Put a key/float pair in the JSONObject.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            A float which is the value.
	     * @return this.
	     * @throws JSONException
	     *             If the key is null or if the number is invalid.
	     */
	    public JSONObject put(String key, float value) throws JSONException {
	        this.put(key, Float.valueOf(value));
	        return this;
	    }

	    /**
	     * Put a key/int pair in the JSONObject.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            An int which is the value.
	     * @return this.
	     * @throws JSONException
	     *             If the key is null.
	     */
	    public JSONObject put(String key, int value) throws JSONException {
	        this.put(key, Integer.valueOf(value));
	        return this;
	    }

	    /**
	     * Put a key/long pair in the JSONObject.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            A long which is the value.
	     * @return this.
	     * @throws JSONException
	     *             If the key is null.
	     */
	    public JSONObject put(String key, long value) throws JSONException {
	        this.put(key, Long.valueOf(value));
	        return this;
	    }

	    /**
	     * Put a key/value pair in the JSONObject, where the value will be a
	     * JSONObject which is produced from a Map.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            A Map value.
	     * @return this.
	     * @throws JSONException
	     */
	    public JSONObject put(String key, Map<?, ?> value) throws JSONException {
	        this.put(key, new JSONObject(value));
	        return this;
	    }

	    /**
	     * Put a key/value pair in the JSONObject. If the value is null, then the
	     * key will be removed from the JSONObject if it is present.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            An object which is the value. It should be of one of these
	     *            types: Boolean, Double, Integer, JSONArray, JSONObject, Long,
	     *            String, or the NULL object.
	     * @return this.
	     * @throws JSONException
	     *             If the value is non-finite number or if the key is null.
	     */
	    public JSONObject put(String key, Object value) throws JSONException {
	        if (key == null) {
	            throw new NullPointerException("Null key.");
	        }
	        if (value != null) {
	            testValidity(value);
	            this.map.put(key, value);
	        } else {
	            this.remove(key);
	        }
	        return this;
	    }

	    /**
	     * Put a key/value pair in the JSONObject, but only if the key and the value
	     * are both non-null, and only if there is not already a member with that
	     * name.
	     *
	     * @param key string
	     * @param value object
	     * @return this.
	     * @throws JSONException
	     *             if the key is a duplicate
	     */
	    public JSONObject putOnce(String key, Object value) throws JSONException {
	        if (key != null && value != null) {
	            if (this.opt(key) != null) {
	                throw new JSONException("Duplicate key \"" + key + "\"");
	            }
	            this.put(key, value);
	        }
	        return this;
	    }

	    /**
	     * Put a key/value pair in the JSONObject, but only if the key and the value
	     * are both non-null.
	     *
	     * @param key
	     *            A key string.
	     * @param value
	     *            An object which is the value. It should be of one of these
	     *            types: Boolean, Double, Integer, JSONArray, JSONObject, Long,
	     *            String, or the NULL object.
	     * @return this.
	     * @throws JSONException
	     *             If the value is a non-finite number.
	     */
	    public JSONObject putOpt(String key, Object value) throws JSONException {
	        if (key != null && value != null) {
	            this.put(key, value);
	        }
	        return this;
	    }

	    /**
	     * Creates a JSONPointer using an initialization string and tries to 
	     * match it to an item within this JSONObject. For example, given a
	     * JSONObject initialized with this document:
	     * <pre>
	     * {
	     *     "a":{"b":"c"}
	     * }
	     * </pre>
	     * and this JSONPointer string: 
	     * <pre>
	     * "/a/b"
	     * </pre>
	     * Then this method will return the String "c".
	     * A JSONPointerException may be thrown from code called by this method.
	     *   
	     * @param jsonPointer string that can be used to create a JSONPointer
	     * @return the item matched by the JSONPointer, otherwise null
	     */
	    public Object query(String jsonPointer) {
	        return query(new JSONPointer(jsonPointer));
	    }
	    /**
	     * Uses a user initialized JSONPointer  and tries to 
	     * match it to an item within this JSONObject. For example, given a
	     * JSONObject initialized with this document:
	     * <pre>
	     * {
	     *     "a":{"b":"c"}
	     * }
	     * </pre>
	     * and this JSONPointer: 
	     * <pre>
	     * "/a/b"
	     * </pre>
	     * Then this method will return the String "c".
	     * A JSONPointerException may be thrown from code called by this method.
	     *   
	     * @param jsonPointer string that can be used to create a JSONPointer
	     * @return the item matched by the JSONPointer, otherwise null
	     */
	    public Object query(JSONPointer jsonPointer) {
	        return jsonPointer.queryFrom(this);
	    }
	    
	    /**
	     * Queries and returns a value from this object using {@code jsonPointer}, or
	     * returns null if the query fails due to a missing key.
	     * 
	     * @param jsonPointer the string representation of the JSON pointer
	     * @return the queried value or {@code null}
	     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
	     */
	    public Object optQuery(String jsonPointer) {
	    	return optQuery(new JSONPointer(jsonPointer));
	    }
	    
	    /**
	     * Queries and returns a value from this object using {@code jsonPointer}, or
	     * returns null if the query fails due to a missing key.
	     * 
	     * @param jsonPointer The JSON pointer
	     * @return the queried value or {@code null}
	     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
	     */
	    public Object optQuery(JSONPointer jsonPointer) {
	        try {
	            return jsonPointer.queryFrom(this);
	        } catch (JSONPointerException e) {
	            return null;
	        }
	    }


	    /**
	     * Remove a name and its value, if present.
	     *
	     * @param key
	     *            The name to be removed.
	     * @return The value that was associated with the name, or null if there was
	     *         no value.
	     */
	    public Object remove(String key) {
	        return this.map.remove(key);
	    }

	    /**
	     * Determine if two JSONObjects are similar.
	     * They must contain the same set of names which must be associated with
	     * similar values.
	     *
	     * @param other The other JSONObject
	     * @return true if they are equal
	     */
	    public boolean similar(Object other) {
	        try {
	            if (!(other instanceof JSONObject)) {
	                return false;
	            }
	            if (!this.keySet().equals(((JSONObject)other).keySet())) {
	                return false;
	            }
	            for (final HashMap.Entry<String,?> entry : this.entrySet()) {
	                String name = entry.getKey();
	                Object valueThis = entry.getValue();
	                Object valueOther = ((JSONObject)other).get(name);
	                if(valueThis == valueOther) {
	                	continue;
	                }
	                if(valueThis == null) {
	                	return false;
	                }
	                if (valueThis instanceof JSONObject) {
	                    if (!((JSONObject)valueThis).similar(valueOther)) {
	                        return false;
	                    }
	                } else if (valueThis instanceof JSONArray) {
	                    if (!((JSONArray)valueThis).similar(valueOther)) {
	                        return false;
	                    }
	                } else if (!valueThis.equals(valueOther)) {
	                    return false;
	                }
	            }
	            return true;
	        } catch (Throwable exception) {
	            return false;
	        }
	    }
	    
	    

	
	    /**
	     * Produce a JSONArray containing the values of the members of this
	     * JSONObject.
	     *
	     * @param names
	     *            A JSONArray containing a list of key strings. This determines
	     *            the sequence of the values in the result.
	     * @return A JSONArray of values.
	     * @throws JSONException
	     *             If any of the values are non-finite numbers.
	     */
	    public JSONArray toJSONArray(JSONArray names) throws JSONException {
	        if (names == null || names.length() == 0) {
	            return null;
	        }
	        JSONArray ja = new JSONArray();
	        for (int i = 0; i < names.length(); i += 1) {
	            ja.put(this.opt(names.getString(i)));
	        }
	        return ja;
	    }

	    /**
	     * Make a JSON text of this JSONObject. For compactness, no whitespace is
	     * added. If this would not result in a syntactically correct JSON text,
	     * then null will be returned instead.
	     * <p><b>
	     * Warning: This method assumes that the data structure is acyclical.
	     * </b>
	     * 
	     * @return a printable, displayable, portable, transmittable representation
	     *         of the object, beginning with <code>{</code>&nbsp;<small>(left
	     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
	     *         brace)</small>.
	     */
	    @Override
	    public String toString() {
	        try {
	            return this.toString(0);
	        } catch (Exception e) {
	            return null;
	        }
	    }

	    /**
	     * Make a pretty-printed JSON text of this JSONObject.
	     * 
	     * <p>If <code>indentFactor > 0</code> and the {@link JSONObject}
	     * has only one key, then the object will be output on a single line:
	     * <pre>{@code {"key": 1}}</pre>
	     * 
	     * <p>If an object has 2 or more keys, then it will be output across
	     * multiple lines: <code><pre>{
	     *  "key1": 1,
	     *  "key2": "value 2",
	     *  "key3": 3
	     * }</pre></code>
	     * <p><b>
	     * Warning: This method assumes that the data structure is acyclical.
	     * </b>
	     *
	     * @param indentFactor
	     *            The number of spaces to add to each level of indentation.
	     * @return a printable, displayable, portable, transmittable representation
	     *         of the object, beginning with <code>{</code>&nbsp;<small>(left
	     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
	     *         brace)</small>.
	     * @throws JSONException
	     *             If the object contains an invalid number.
	     */
	    public String toString(int indentFactor) throws JSONException {
	        StringWriter w = new StringWriter();
	        synchronized (w.getBuffer()) {
	            return this.write(w, indentFactor, 0).toString();
	        }
	    }



	    /**
	     * Write the contents of the JSONObject as JSON text to a writer. For
	     * compactness, no whitespace is added.
	     * <p><b>
	     * Warning: This method assumes that the data structure is acyclical.
	     * </b>
	     * 
	     * @return The writer.
	     * @throws JSONException
	     */
	    public Writer write(Writer writer) throws JSONException {
	        return this.write(writer, 0, 0);
	    }


	    /**
	     * Write the contents of the JSONObject as JSON text to a writer.
	     * 
	     * <p>If <code>indentFactor > 0</code> and the {@link JSONObject}
	     * has only one key, then the object will be output on a single line:
	     * <pre>{@code {"key": 1}}</pre>
	     * 
	     * <p>If an object has 2 or more keys, then it will be output across
	     * multiple lines: <code><pre>{
	     *  "key1": 1,
	     *  "key2": "value 2",
	     *  "key3": 3
	     * }</pre></code>
	     * <p><b>
	     * Warning: This method assumes that the data structure is acyclical.
	     * </b>
	     *
	     * @param writer
	     *            Writes the serialized JSON
	     * @param indentFactor
	     *            The number of spaces to add to each level of indentation.
	     * @param indent
	     *            The indentation of the top level.
	     * @return The writer.
	     * @throws JSONException
	     */
	    public Writer write(Writer writer, int indentFactor, int indent)
	            throws JSONException {
	        try {
	            boolean commanate = false;
	            final int length = this.length();
	            writer.write('{');

	            if (length == 1) {
	            	final HashMap.Entry<String,?> entry = this.entrySet().iterator().next();
	                final String key = entry.getKey();
	                writer.write(quote(key));
	                writer.write(':');
	                if (indentFactor > 0) {
	                    writer.write(' ');
	                }
	                try{
	                    writeValue(writer, entry.getValue(), indentFactor, indent);
	                } catch (Exception e) {
	                    throw new JSONException("Unable to write JSONObject value for key: " + key, e);
	                }
	            } else if (length != 0) {
	                final int newindent = indent + indentFactor;
	                for (final HashMap.Entry<String,?> entry : this.entrySet()) {
	                    if (commanate) {
	                        writer.write(',');
	                    }
	                    if (indentFactor > 0) {
	                        writer.write('\n');
	                    }
	                    indent(writer, newindent);
	                    final String key = entry.getKey();
	                    writer.write(quote(key));
	                    writer.write(':');
	                    if (indentFactor > 0) {
	                        writer.write(' ');
	                    }
	                    try {
	                        writeValue(writer, entry.getValue(), indentFactor, newindent);
	                    } catch (Exception e) {
	                        throw new JSONException("Unable to write JSONObject value for key: " + key, e);
	                    }
	                    commanate = true;
	                }
	                if (indentFactor > 0) {
	                    writer.write('\n');
	                }
	                indent(writer, indent);
	            }
	            writer.write('}');
	            return writer;
	        } catch (IOException exception) {
	            throw new JSONException(exception);
	        }
	    }

	    /**
	     * Returns a java.util.Map containing all of the entries in this object.
	     * If an entry in the object is a JSONArray or JSONObject it will also
	     * be converted.
	     * <p>
	     * Warning: This method assumes that the data structure is acyclical.
	     *
	     * @return a java.util.Map containing the entries of this object
	     */
	    public Map<String, Object> toMap() {
	        Map<String, Object> results = new HashMap<String, Object>();
	        for (HashMap.Entry<String, Object> entry : this.entrySet()) {
	            Object value;
	            if (entry.getValue() == null || NULL.equals(entry.getValue())) {
	                value = null;
	            } else if (entry.getValue() instanceof JSONObject) {
	                value = ((JSONObject) entry.getValue()).toMap();
	            } else if (entry.getValue() instanceof JSONArray) {
	                value = ((JSONArray) entry.getValue()).toList();
	            } else {
	                value = entry.getValue();
	            }
	            results.put(entry.getKey(), value);
	        }
	        return results;
	    }
	}
	
	/**
	 * A JSONArray is an ordered sequence of values. Its external text form is a
	 * string wrapped in square brackets with commas separating the values. The
	 * internal form is an object having <code>get</code> and <code>opt</code>
	 * methods for accessing the values by index, and <code>put</code> methods for
	 * adding or replacing values. The values can be any of these types:
	 * <code>Boolean</code>, <code>JSONArray</code>, <code>JSONObject</code>,
	 * <code>Number</code>, <code>String</code>, or the
	 * <code>NULL object</code>.
	 * <p>
	 * The constructor can convert a JSON text into a Java object. The
	 * <code>toString</code> method converts to JSON text.
	 * <p>
	 * A <code>get</code> method returns a value if one can be found, and throws an
	 * exception if one cannot be found. An <code>opt</code> method returns a
	 * default value instead of throwing an exception, and so is useful for
	 * obtaining optional values.
	 * <p>
	 * The generic <code>get()</code> and <code>opt()</code> methods return an
	 * object which you can cast or query for type. There are also typed
	 * <code>get</code> and <code>opt</code> methods that do type checking and type
	 * coercion for you.
	 * <p>
	 * The texts produced by the <code>toString</code> methods strictly conform to
	 * JSON syntax rules. The constructors are more forgiving in the texts they will
	 * accept:
	 * <ul>
	 * <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may appear just
	 * before the closing bracket.</li>
	 * <li>The <code>null</code> value will be inserted when there is <code>,</code>
	 * &nbsp;<small>(comma)</small> elision.</li>
	 * <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
	 * quote)</small>.</li>
	 * <li>Strings do not need to be quoted at all if they do not begin with a quote
	 * or single quote, and if they do not contain leading or trailing spaces, and
	 * if they do not contain any of these characters:
	 * <code>{ } [ ] / \ : , #</code> and if they do not look like numbers and
	 * if they are not the reserved words <code>true</code>, <code>false</code>, or
	 * <code>null</code>.</li>
	 * </ul>
	 *
	 * @author JSON.org
	 * @version 2016-08/15
	 */
	public static class JSONArray implements Iterable<Object> {

	    /**
	     * The arrayList where the JSONArray's properties are kept.
	     */
	    private final ArrayList<Object> myArrayList;

	    /**
	     * Construct an empty JSONArray.
	     */
	    public JSONArray() {
	        this.myArrayList = new ArrayList<Object>();
	    }

	    /**
	     * Construct a JSONArray from a JSONTokener.
	     *
	     * @param x
	     *            A JSONTokener
	     * @throws JSONException
	     *             If there is a syntax error.
	     */
	    public JSONArray(JSONTokener x) throws JSONException {
	        this();
	        if (x.nextClean() != '[') {
	            throw x.syntaxError("A JSONArray text must start with '['");
	        }
	        
	        char nextChar = x.nextClean();
	        if (nextChar == 0) {
	            // array is unclosed. No ']' found, instead EOF
	            throw x.syntaxError("Expected a ',' or ']'");
	        }
	        if (nextChar != ']') {
	            x.back();
	            for (;;) {
	                if (x.nextClean() == ',') {
	                    x.back();
	                    this.myArrayList.add(NULL);
	                } else {
	                    x.back();
	                    this.myArrayList.add(x.nextValue());
	                }
	                switch (x.nextClean()) {
	                case 0:
	                    // array is unclosed. No ']' found, instead EOF
	                    throw x.syntaxError("Expected a ',' or ']'");
	                case ',':
	                    nextChar = x.nextClean();
	                    if (nextChar == 0) {
	                        // array is unclosed. No ']' found, instead EOF
	                        throw x.syntaxError("Expected a ',' or ']'");
	                    }
	                    if (nextChar == ']') {
	                        return;
	                    }
	                    x.back();
	                    break;
	                case ']':
	                    return;
	                default:
	                    throw x.syntaxError("Expected a ',' or ']'");
	                }
	            }
	        }
	    }

	    /**
	     * Construct a JSONArray from a source JSON text.
	     *
	     * @param source
	     *            A string that begins with <code>[</code>&nbsp;<small>(left
	     *            bracket)</small> and ends with <code>]</code>
	     *            &nbsp;<small>(right bracket)</small>.
	     * @throws JSONException
	     *             If there is a syntax error.
	     */
	    public JSONArray(String source) throws JSONException {
	        this(new JSONTokener(source));
	    }

	    /**
	     * Construct a JSONArray from a Collection.
	     *
	     * @param collection
	     *            A Collection.
	     */
	    public JSONArray(Collection<?> collection) {
	        if (collection == null) {
	            this.myArrayList = new ArrayList<Object>();
	        } else {
	            this.myArrayList = new ArrayList<Object>(collection.size());
	        	for (Object o: collection){
	        		this.myArrayList.add(wrap(o));
	        	}
	        }
	    }

	    /**
	     * Construct a JSONArray from an array
	     *
	     * @throws JSONException
	     *             If not an array.
	     */
	    public JSONArray(Object array) throws JSONException {
	        this();
	        if (array.getClass().isArray()) {
	            int length = Array.getLength(array);
	            this.myArrayList.ensureCapacity(length);
	            for (int i = 0; i < length; i += 1) {
	                this.put(wrap(Array.get(array, i)));
	            }
	        } else {
	            throw new JSONException(
	                    "JSONArray initial value should be a string or collection or array.");
	        }
	    }

	    @Override
	    public Iterator<Object> iterator() {
	        return this.myArrayList.iterator();
	    }

	    /**
	     * Get the object value associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return An object value.
	     * @throws JSONException
	     *             If there is no value for the index.
	     */
	    public Object get(int index) throws JSONException {
	        Object object = this.opt(index);
	        if (object == null) {
	            throw new JSONException("JSONArray[" + index + "] not found.");
	        }
	        return object;
	    }

	    /**
	     * Get the boolean value associated with an index. The string values "true"
	     * and "false" are converted to boolean.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The truth.
	     * @throws JSONException
	     *             If there is no value for the index or if the value is not
	     *             convertible to boolean.
	     */
	    public boolean getBoolean(int index) throws JSONException {
	        Object object = this.get(index);
	        if (object.equals(Boolean.FALSE)
	                || (object instanceof String && ((String) object)
	                        .equalsIgnoreCase("false"))) {
	            return false;
	        } else if (object.equals(Boolean.TRUE)
	                || (object instanceof String && ((String) object)
	                        .equalsIgnoreCase("true"))) {
	            return true;
	        }
	        throw new JSONException("JSONArray[" + index + "] is not a boolean.");
	    }

	    /**
	     * Get the double value associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     * @throws JSONException
	     *             If the key is not found or if the value cannot be converted
	     *             to a number.
	     */
	    public double getDouble(int index) throws JSONException {
	        Object object = this.get(index);
	        try {
	            return object instanceof Number ? ((Number) object).doubleValue()
	                    : Double.parseDouble((String) object);
	        } catch (Exception e) {
	            throw new JSONException("JSONArray[" + index + "] is not a number.", e);
	        }
	    }

	    /**
	     * Get the float value associated with a key.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The numeric value.
	     * @throws JSONException
	     *             if the key is not found or if the value is not a Number
	     *             object and cannot be converted to a number.
	     */
	    public float getFloat(int index) throws JSONException {
	        Object object = this.get(index);
	        try {
	            return object instanceof Number ? ((Number) object).floatValue()
	                    : Float.parseFloat(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONArray[" + index
	                    + "] is not a number.", e);
	        }
	    }

	    /**
	     * Get the Number value associated with a key.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The numeric value.
	     * @throws JSONException
	     *             if the key is not found or if the value is not a Number
	     *             object and cannot be converted to a number.
	     */
	    public Number getNumber(int index) throws JSONException {
	        Object object = this.get(index);
	        try {
	            if (object instanceof Number) {
	                return (Number)object;
	            }
	            return stringToNumber(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONArray[" + index + "] is not a number.", e);
	        }
	    }

	    /**
	    * Get the enum value associated with an index.
	    * 
	    * @param clazz
	    *            The type of enum to retrieve.
	    * @param index
	    *            The index must be between 0 and length() - 1.
	    * @return The enum value at the index location
	    * @throws JSONException
	    *            if the key is not found or if the value cannot be converted
	    *            to an enum.
	    */
	    public <E extends Enum<E>> E getEnum(Class<E> clazz, int index) throws JSONException {
	        E val = optEnum(clazz, index);
	        if(val==null) {
	            // JSONException should really take a throwable argument.
	            // If it did, I would re-implement this with the Enum.valueOf
	            // method and place any thrown exception in the JSONException
	            throw new JSONException("JSONArray[" + index + "] is not an enum of type "
	                    + quote(clazz.getSimpleName()) + ".");
	        }
	        return val;
	    }

	    /**
	     * Get the BigDecimal value associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     * @throws JSONException
	     *             If the key is not found or if the value cannot be converted
	     *             to a BigDecimal.
	     */
	    public BigDecimal getBigDecimal (int index) throws JSONException {
	        Object object = this.get(index);
	        try {
	            return new BigDecimal(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONArray[" + index +
	                    "] could not convert to BigDecimal.", e);
	        }
	    }

	    /**
	     * Get the BigInteger value associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     * @throws JSONException
	     *             If the key is not found or if the value cannot be converted
	     *             to a BigInteger.
	     */
	    public BigInteger getBigInteger (int index) throws JSONException {
	        Object object = this.get(index);
	        try {
	            return new BigInteger(object.toString());
	        } catch (Exception e) {
	            throw new JSONException("JSONArray[" + index +
	                    "] could not convert to BigInteger.", e);
	        }
	    }

	    /**
	     * Get the int value associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     * @throws JSONException
	     *             If the key is not found or if the value is not a number.
	     */
	    public int getInt(int index) throws JSONException {
	        Object object = this.get(index);
	        try {
	            return object instanceof Number ? ((Number) object).intValue()
	                    : Integer.parseInt((String) object);
	        } catch (Exception e) {
	            throw new JSONException("JSONArray[" + index + "] is not a number.", e);
	        }
	    }

	    /**
	     * Get the JSONArray associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return A JSONArray value.
	     * @throws JSONException
	     *             If there is no value for the index. or if the value is not a
	     *             JSONArray
	     */
	    public JSONArray getJSONArray(int index) throws JSONException {
	        Object object = this.get(index);
	        if (object instanceof JSONArray) {
	            return (JSONArray) object;
	        }
	        throw new JSONException("JSONArray[" + index + "] is not a JSONArray.");
	    }

	    /**
	     * Get the JSONObject associated with an index.
	     *
	     * @param index
	     *            subscript
	     * @return A JSONObject value.
	     * @throws JSONException
	     *             If there is no value for the index or if the value is not a
	     *             JSONObject
	     */
	    public JSONObject getJSONObject(int index) throws JSONException {
	        Object object = this.get(index);
	        if (object instanceof JSONObject) {
	            return (JSONObject) object;
	        }
	        throw new JSONException("JSONArray[" + index + "] is not a JSONObject.");
	    }

	    /**
	     * Get the long value associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     * @throws JSONException
	     *             If the key is not found or if the value cannot be converted
	     *             to a number.
	     */
	    public long getLong(int index) throws JSONException {
	        Object object = this.get(index);
	        try {
	            return object instanceof Number ? ((Number) object).longValue()
	                    : Long.parseLong((String) object);
	        } catch (Exception e) {
	            throw new JSONException("JSONArray[" + index + "] is not a number.", e);
	        }
	    }

	    /**
	     * Get the string associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return A string value.
	     * @throws JSONException
	     *             If there is no string value for the index.
	     */
	    public String getString(int index) throws JSONException {
	        Object object = this.get(index);
	        if (object instanceof String) {
	            return (String) object;
	        }
	        throw new JSONException("JSONArray[" + index + "] not a string.");
	    }

	    /**
	     * Determine if the value is null.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return true if the value at the index is null, or if there is no value.
	     */
	    public boolean isNull(int index) {
	        return NULL.equals(this.opt(index));
	    }

	    /**
	     * Make a string from the contents of this JSONArray. The
	     * <code>separator</code> string is inserted between each element. Warning:
	     * This method assumes that the data structure is acyclical.
	     *
	     * @param separator
	     *            A string that will be inserted between the elements.
	     * @return a string.
	     * @throws JSONException
	     *             If the array contains an invalid number.
	     */
	    public String join(String separator) throws JSONException {
	        int len = this.length();
	        StringBuilder sb = new StringBuilder();

	        for (int i = 0; i < len; i += 1) {
	            if (i > 0) {
	                sb.append(separator);
	            }
	            sb.append(valueToString(this.myArrayList.get(i)));
	        }
	        return sb.toString();
	    }

	    /**
	     * Get the number of elements in the JSONArray, included nulls.
	     *
	     * @return The length (or size).
	     */
	    public int length() {
	        return this.myArrayList.size();
	    }

	    /**
	     * Get the optional object value associated with an index.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1. If not, null is returned.
	     * @return An object value, or null if there is no object at that index.
	     */
	    public Object opt(int index) {
	        return (index < 0 || index >= this.length()) ? null : this.myArrayList
	                .get(index);
	    }

	    /**
	     * Get the optional boolean value associated with an index. It returns false
	     * if there is no value at that index, or if the value is not Boolean.TRUE
	     * or the String "true".
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The truth.
	     */
	    public boolean optBoolean(int index) {
	        return this.optBoolean(index, false);
	    }

	    /**
	     * Get the optional boolean value associated with an index. It returns the
	     * defaultValue if there is no value at that index or if it is not a Boolean
	     * or the String "true" or "false" (case insensitive).
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @param defaultValue
	     *            A boolean default.
	     * @return The truth.
	     */
	    public boolean optBoolean(int index, boolean defaultValue) {
	        try {
	            return this.getBoolean(index);
	        } catch (Exception e) {
	            return defaultValue;
	        }
	    }

	    /**
	     * Get the optional double value associated with an index. NaN is returned
	     * if there is no value for the index, or if the value is not a number and
	     * cannot be converted to a number.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     */
	    public double optDouble(int index) {
	        return this.optDouble(index, Double.NaN);
	    }

	    /**
	     * Get the optional double value associated with an index. The defaultValue
	     * is returned if there is no value for the index, or if the value is not a
	     * number and cannot be converted to a number.
	     *
	     * @param index
	     *            subscript
	     * @param defaultValue
	     *            The default value.
	     * @return The value.
	     */
	    public double optDouble(int index, double defaultValue) {
	        Object val = this.opt(index);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return ((Number) val).doubleValue();
	        }
	        if (val instanceof String) {
	            try {
	                return Double.parseDouble((String) val);
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }

	    /**
	     * Get the optional float value associated with an index. NaN is returned
	     * if there is no value for the index, or if the value is not a number and
	     * cannot be converted to a number.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     */
	    public float optFloat(int index) {
	        return this.optFloat(index, Float.NaN);
	    }

	    /**
	     * Get the optional float value associated with an index. The defaultValue
	     * is returned if there is no value for the index, or if the value is not a
	     * number and cannot be converted to a number.
	     *
	     * @param index
	     *            subscript
	     * @param defaultValue
	     *            The default value.
	     * @return The value.
	     */
	    public float optFloat(int index, float defaultValue) {
	        Object val = this.opt(index);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return ((Number) val).floatValue();
	        }
	        if (val instanceof String) {
	            try {
	                return Float.parseFloat((String) val);
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }

	    /**
	     * Get the optional int value associated with an index. Zero is returned if
	     * there is no value for the index, or if the value is not a number and
	     * cannot be converted to a number.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     */
	    public int optInt(int index) {
	        return this.optInt(index, 0);
	    }

	    /**
	     * Get the optional int value associated with an index. The defaultValue is
	     * returned if there is no value for the index, or if the value is not a
	     * number and cannot be converted to a number.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @param defaultValue
	     *            The default value.
	     * @return The value.
	     */
	    public int optInt(int index, int defaultValue) {
	        Object val = this.opt(index);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return ((Number) val).intValue();
	        }
	        
	        if (val instanceof String) {
	            try {
	                return new BigDecimal(val.toString()).intValue();
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }

	    /**
	     * Get the enum value associated with a key.
	     * 
	     * @param clazz
	     *            The type of enum to retrieve.
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The enum value at the index location or null if not found
	     */
	    public <E extends Enum<E>> E optEnum(Class<E> clazz, int index) {
	        return this.optEnum(clazz, index, null);
	    }

	    /**
	     * Get the enum value associated with a key.
	     * 
	     * @param clazz
	     *            The type of enum to retrieve.
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @param defaultValue
	     *            The default in case the value is not found
	     * @return The enum value at the index location or defaultValue if
	     *            the value is not found or cannot be assigned to clazz
	     */
	    public <E extends Enum<E>> E optEnum(Class<E> clazz, int index, E defaultValue) {
	        try {
	            Object val = this.opt(index);
	            if (NULL.equals(val)) {
	                return defaultValue;
	            }
	            if (clazz.isAssignableFrom(val.getClass())) {
	                // we just checked it!
	                @SuppressWarnings("unchecked")
	                E myE = (E) val;
	                return myE;
	            }
	            return Enum.valueOf(clazz, val.toString());
	        } catch (IllegalArgumentException e) {
	            return defaultValue;
	        } catch (NullPointerException e) {
	            return defaultValue;
	        }
	    }


	    /**
	     * Get the optional BigInteger value associated with an index. The 
	     * defaultValue is returned if there is no value for the index, or if the 
	     * value is not a number and cannot be converted to a number.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @param defaultValue
	     *            The default value.
	     * @return The value.
	     */
	    public BigInteger optBigInteger(int index, BigInteger defaultValue) {
	        Object val = this.opt(index);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof BigInteger){
	            return (BigInteger) val;
	        }
	        if (val instanceof BigDecimal){
	            return ((BigDecimal) val).toBigInteger();
	        }
	        if (val instanceof Double || val instanceof Float){
	            return new BigDecimal(((Number) val).doubleValue()).toBigInteger();
	        }
	        if (val instanceof Long || val instanceof Integer
	                || val instanceof Short || val instanceof Byte){
	            return BigInteger.valueOf(((Number) val).longValue());
	        }
	        try {
	            final String valStr = val.toString();
	            if(isDecimalNotation(valStr)) {
	                return new BigDecimal(valStr).toBigInteger();
	            }
	            return new BigInteger(valStr);
	        } catch (Exception e) {
	            return defaultValue;
	        }
	    }

	    /**
	     * Get the optional BigDecimal value associated with an index. The 
	     * defaultValue is returned if there is no value for the index, or if the 
	     * value is not a number and cannot be converted to a number.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @param defaultValue
	     *            The default value.
	     * @return The value.
	     */
	    public BigDecimal optBigDecimal(int index, BigDecimal defaultValue) {
	        Object val = this.opt(index);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof BigDecimal){
	            return (BigDecimal) val;
	        }
	        if (val instanceof BigInteger){
	            return new BigDecimal((BigInteger) val);
	        }
	        if (val instanceof Double || val instanceof Float){
	            return new BigDecimal(((Number) val).doubleValue());
	        }
	        if (val instanceof Long || val instanceof Integer
	                || val instanceof Short || val instanceof Byte){
	            return new BigDecimal(((Number) val).longValue());
	        }
	        try {
	            return new BigDecimal(val.toString());
	        } catch (Exception e) {
	            return defaultValue;
	        }
	    }

	    /**
	     * Get the optional JSONArray associated with an index.
	     *
	     * @param index
	     *            subscript
	     * @return A JSONArray value, or null if the index has no value, or if the
	     *         value is not a JSONArray.
	     */
	    public JSONArray optJSONArray(int index) {
	        Object o = this.opt(index);
	        return o instanceof JSONArray ? (JSONArray) o : null;
	    }

	    /**
	     * Get the optional JSONObject associated with an index. Null is returned if
	     * the key is not found, or null if the index has no value, or if the value
	     * is not a JSONObject.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return A JSONObject value.
	     */
	    public JSONObject optJSONObject(int index) {
	        Object o = this.opt(index);
	        return o instanceof JSONObject ? (JSONObject) o : null;
	    }

	    /**
	     * Get the optional long value associated with an index. Zero is returned if
	     * there is no value for the index, or if the value is not a number and
	     * cannot be converted to a number.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return The value.
	     */
	    public long optLong(int index) {
	        return this.optLong(index, 0);
	    }

	    /**
	     * Get the optional long value associated with an index. The defaultValue is
	     * returned if there is no value for the index, or if the value is not a
	     * number and cannot be converted to a number.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @param defaultValue
	     *            The default value.
	     * @return The value.
	     */
	    public long optLong(int index, long defaultValue) {
	        Object val = this.opt(index);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return ((Number) val).longValue();
	        }
	        
	        if (val instanceof String) {
	            try {
	                return new BigDecimal(val.toString()).longValue();
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }

	    /**
	     * Get an optional {@link Number} value associated with a key, or <code>null</code>
	     * if there is no such key or if the value is not a number. If the value is a string,
	     * an attempt will be made to evaluate it as a number ({@link BigDecimal}). This method
	     * would be used in cases where type coercion of the number value is unwanted.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return An object which is the value.
	     */
	    public Number optNumber(int index) {
	        return this.optNumber(index, null);
	    }

	    /**
	     * Get an optional {@link Number} value associated with a key, or the default if there
	     * is no such key or if the value is not a number. If the value is a string,
	     * an attempt will be made to evaluate it as a number ({@link BigDecimal}). This method
	     * would be used in cases where type coercion of the number value is unwanted.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @param defaultValue
	     *            The default.
	     * @return An object which is the value.
	     */
	    public Number optNumber(int index, Number defaultValue) {
	        Object val = this.opt(index);
	        if (NULL.equals(val)) {
	            return defaultValue;
	        }
	        if (val instanceof Number){
	            return (Number) val;
	        }
	        
	        if (val instanceof String) {
	            try {
	                return stringToNumber((String) val);
	            } catch (Exception e) {
	                return defaultValue;
	            }
	        }
	        return defaultValue;
	    }

	    /**
	     * Get the optional string value associated with an index. It returns an
	     * empty string if there is no value at that index. If the value is not a
	     * string and is not null, then it is converted to a string.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @return A String value.
	     */
	    public String optString(int index) {
	        return this.optString(index, "");
	    }

	    /**
	     * Get the optional string associated with an index. The defaultValue is
	     * returned if the key is not found.
	     *
	     * @param index
	     *            The index must be between 0 and length() - 1.
	     * @param defaultValue
	     *            The default value.
	     * @return A String value.
	     */
	    public String optString(int index, String defaultValue) {
	        Object object = this.opt(index);
	        return NULL.equals(object) ? defaultValue : object
	                .toString();
	    }

	    /**
	     * Append a boolean value. This increases the array's length by one.
	     *
	     * @param value
	     *            A boolean value.
	     * @return this.
	     */
	    public JSONArray put(boolean value) {
	        this.put(value ? Boolean.TRUE : Boolean.FALSE);
	        return this;
	    }

	    /**
	     * Put a value in the JSONArray, where the value will be a JSONArray which
	     * is produced from a Collection.
	     *
	     * @param value
	     *            A Collection value.
	     * @return this.
	     */
	    public JSONArray put(Collection<?> value) {
	        this.put(new JSONArray(value));
	        return this;
	    }

	    /**
	     * Append a double value. This increases the array's length by one.
	     *
	     * @param value
	     *            A double value.
	     * @throws JSONException
	     *             if the value is not finite.
	     * @return this.
	     */
	    public JSONArray put(double value) throws JSONException {
	        Double d = new Double(value);
	        testValidity(d);
	        this.put(d);
	        return this;
	    }

	    /**
	     * Append an int value. This increases the array's length by one.
	     *
	     * @param value
	     *            An int value.
	     * @return this.
	     */
	    public JSONArray put(int value) {
	        this.put(new Integer(value));
	        return this;
	    }

	    /**
	     * Append an long value. This increases the array's length by one.
	     *
	     * @param value
	     *            A long value.
	     * @return this.
	     */
	    public JSONArray put(long value) {
	        this.put(new Long(value));
	        return this;
	    }

	    /**
	     * Put a value in the JSONArray, where the value will be a JSONObject which
	     * is produced from a Map.
	     *
	     * @param value
	     *            A Map value.
	     * @return this.
	     */
	    public JSONArray put(Map<?, ?> value) {
	        this.put(new JSONObject(value));
	        return this;
	    }

	    /**
	     * Append an object value. This increases the array's length by one.
	     *
	     * @param value
	     *            An object value. The value should be a Boolean, Double,
	     *            Integer, JSONArray, JSONObject, Long, or String, or the
	     *            NULL object.
	     * @return this.
	     */
	    public JSONArray put(Object value) {
	        this.myArrayList.add(value);
	        return this;
	    }

	    /**
	     * Put or replace a boolean value in the JSONArray. If the index is greater
	     * than the length of the JSONArray, then null elements will be added as
	     * necessary to pad it out.
	     *
	     * @param index
	     *            The subscript.
	     * @param value
	     *            A boolean value.
	     * @return this.
	     * @throws JSONException
	     *             If the index is negative.
	     */
	    public JSONArray put(int index, boolean value) throws JSONException {
	        this.put(index, value ? Boolean.TRUE : Boolean.FALSE);
	        return this;
	    }

	    /**
	     * Put a value in the JSONArray, where the value will be a JSONArray which
	     * is produced from a Collection.
	     *
	     * @param index
	     *            The subscript.
	     * @param value
	     *            A Collection value.
	     * @return this.
	     * @throws JSONException
	     *             If the index is negative or if the value is not finite.
	     */
	    public JSONArray put(int index, Collection<?> value) throws JSONException {
	        this.put(index, new JSONArray(value));
	        return this;
	    }

	    /**
	     * Put or replace a double value. If the index is greater than the length of
	     * the JSONArray, then null elements will be added as necessary to pad it
	     * out.
	     *
	     * @param index
	     *            The subscript.
	     * @param value
	     *            A double value.
	     * @return this.
	     * @throws JSONException
	     *             If the index is negative or if the value is not finite.
	     */
	    public JSONArray put(int index, double value) throws JSONException {
	        this.put(index, new Double(value));
	        return this;
	    }

	    /**
	     * Put or replace an int value. If the index is greater than the length of
	     * the JSONArray, then null elements will be added as necessary to pad it
	     * out.
	     *
	     * @param index
	     *            The subscript.
	     * @param value
	     *            An int value.
	     * @return this.
	     * @throws JSONException
	     *             If the index is negative.
	     */
	    public JSONArray put(int index, int value) throws JSONException {
	        this.put(index, new Integer(value));
	        return this;
	    }

	    /**
	     * Put or replace a long value. If the index is greater than the length of
	     * the JSONArray, then null elements will be added as necessary to pad it
	     * out.
	     *
	     * @param index
	     *            The subscript.
	     * @param value
	     *            A long value.
	     * @return this.
	     * @throws JSONException
	     *             If the index is negative.
	     */
	    public JSONArray put(int index, long value) throws JSONException {
	        this.put(index, new Long(value));
	        return this;
	    }

	    /**
	     * Put a value in the JSONArray, where the value will be a JSONObject that
	     * is produced from a Map.
	     *
	     * @param index
	     *            The subscript.
	     * @param value
	     *            The Map value.
	     * @return this.
	     * @throws JSONException
	     *             If the index is negative or if the the value is an invalid
	     *             number.
	     */
	    public JSONArray put(int index, Map<?, ?> value) throws JSONException {
	        this.put(index, new JSONObject(value));
	        return this;
	    }

	    /**
	     * Put or replace an object value in the JSONArray. If the index is greater
	     * than the length of the JSONArray, then null elements will be added as
	     * necessary to pad it out.
	     *
	     * @param index
	     *            The subscript.
	     * @param value
	     *            The value to put into the array. The value should be a
	     *            Boolean, Double, Integer, JSONArray, JSONObject, Long, or
	     *            String, or the NULL object.
	     * @return this.
	     * @throws JSONException
	     *             If the index is negative or if the the value is an invalid
	     *             number.
	     */
	    public JSONArray put(int index, Object value) throws JSONException {
	        testValidity(value);
	        if (index < 0) {
	            throw new JSONException("JSONArray[" + index + "] not found.");
	        }
	        if (index < this.length()) {
	            this.myArrayList.set(index, value);
	        } else if(index == this.length()){
	            // simple append
	            this.put(value);
	        } else {
	            // if we are inserting past the length, we want to grow the array all at once
	            // instead of incrementally.
	            this.myArrayList.ensureCapacity(index + 1);
	            while (index != this.length()) {
	                this.put(NULL);
	            }
	            this.put(value);
	        }
	        return this;
	    }
	    
	    /**
	     * Creates a JSONPointer using an initialization string and tries to 
	     * match it to an item within this JSONArray. For example, given a
	     * JSONArray initialized with this document:
	     * <pre>
	     * [
	     *     {"b":"c"}
	     * ]
	     * </pre>
	     * and this JSONPointer string: 
	     * <pre>
	     * "/0/b"
	     * </pre>
	     * Then this method will return the String "c"
	     * A JSONPointerException may be thrown from code called by this method.
	     *
	     * @param jsonPointer string that can be used to create a JSONPointer
	     * @return the item matched by the JSONPointer, otherwise null
	     */
	    public Object query(String jsonPointer) {
	        return query(new JSONPointer(jsonPointer));
	    }
	    
	    /**
	     * Uses a uaer initialized JSONPointer  and tries to 
	     * match it to an item whithin this JSONArray. For example, given a
	     * JSONArray initialized with this document:
	     * <pre>
	     * [
	     *     {"b":"c"}
	     * ]
	     * </pre>
	     * and this JSONPointer: 
	     * <pre>
	     * "/0/b"
	     * </pre>
	     * Then this method will return the String "c"
	     * A JSONPointerException may be thrown from code called by this method.
	     *
	     * @param jsonPointer string that can be used to create a JSONPointer
	     * @return the item matched by the JSONPointer, otherwise null
	     */
	    public Object query(JSONPointer jsonPointer) {
	        return jsonPointer.queryFrom(this);
	    }
	    
	    /**
	     * Queries and returns a value from this object using {@code jsonPointer}, or
	     * returns null if the query fails due to a missing key.
	     * 
	     * @param jsonPointer the string representation of the JSON pointer
	     * @return the queried value or {@code null}
	     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
	     */
	    public Object optQuery(String jsonPointer) {
	    	return optQuery(new JSONPointer(jsonPointer));
	    }
	    
	    /**
	     * Queries and returns a value from this object using {@code jsonPointer}, or
	     * returns null if the query fails due to a missing key.
	     * 
	     * @param jsonPointer The JSON pointer
	     * @return the queried value or {@code null}
	     * @throws IllegalArgumentException if {@code jsonPointer} has invalid syntax
	     */
	    public Object optQuery(JSONPointer jsonPointer) {
	        try {
	            return jsonPointer.queryFrom(this);
	        } catch (JSONPointerException e) {
	            return null;
	        }
	    }

	    /**
	     * Remove an index and close the hole.
	     *
	     * @param index
	     *            The index of the element to be removed.
	     * @return The value that was associated with the index, or null if there
	     *         was no value.
	     */
	    public Object remove(int index) {
	        return index >= 0 && index < this.length()
	            ? this.myArrayList.remove(index)
	            : null;
	    }

	    /**
	     * Determine if two JSONArrays are similar.
	     * They must contain similar sequences.
	     *
	     * @param other The other JSONArray
	     * @return true if they are equal
	     */
	    public boolean similar(Object other) {
	        if (!(other instanceof JSONArray)) {
	            return false;
	        }
	        int len = this.length();
	        if (len != ((JSONArray)other).length()) {
	            return false;
	        }
	        for (int i = 0; i < len; i += 1) {
	            Object valueThis = this.myArrayList.get(i);
	            Object valueOther = ((JSONArray)other).myArrayList.get(i);
	            if(valueThis == valueOther) {
	            	continue;
	            }
	            if(valueThis == null) {
	            	return false;
	            }
	            if (valueThis instanceof JSONObject) {
	                if (!((JSONObject)valueThis).similar(valueOther)) {
	                    return false;
	                }
	            } else if (valueThis instanceof JSONArray) {
	                if (!((JSONArray)valueThis).similar(valueOther)) {
	                    return false;
	                }
	            } else if (!valueThis.equals(valueOther)) {
	                return false;
	            }
	        }
	        return true;
	    }

	    /**
	     * Produce a JSONObject by combining a JSONArray of names with the values of
	     * this JSONArray.
	     *
	     * @param names
	     *            A JSONArray containing a list of key strings. These will be
	     *            paired with the values.
	     * @return A JSONObject, or null if there are no names or if this JSONArray
	     *         has no values.
	     * @throws JSONException
	     *             If any of the names are null.
	     */
	    public JSONObject toJSONObject(JSONArray names) throws JSONException {
	        if (names == null || names.length() == 0 || this.length() == 0) {
	            return null;
	        }
	        JSONObject jo = new JSONObject(names.length());
	        for (int i = 0; i < names.length(); i += 1) {
	            jo.put(names.getString(i), this.opt(i));
	        }
	        return jo;
	    }

	    /**
	     * Make a JSON text of this JSONArray. For compactness, no unnecessary
	     * whitespace is added. If it is not possible to produce a syntactically
	     * correct JSON text then null will be returned instead. This could occur if
	     * the array contains an invalid number.
	     * <p><b>
	     * Warning: This method assumes that the data structure is acyclical.
	     * </b>
	     *
	     * @return a printable, displayable, transmittable representation of the
	     *         array.
	     */
	    @Override
	    public String toString() {
	        try {
	            return this.toString(0);
	        } catch (Exception e) {
	            return null;
	        }
	    }

	    /**
	     * Make a pretty-printed JSON text of this JSONArray.
	     * 
	     * <p>If <code>indentFactor > 0</code> and the {@link JSONArray} has only
	     * one element, then the array will be output on a single line:
	     * <pre>{@code [1]}</pre>
	     * 
	     * <p>If an array has 2 or more elements, then it will be output across
	     * multiple lines: <pre>{@code
	     * [
	     * 1,
	     * "value 2",
	     * 3
	     * ]
	     * }</pre>
	     * <p><b>
	     * Warning: This method assumes that the data structure is acyclical.
	     * </b>
	     * 
	     * @param indentFactor
	     *            The number of spaces to add to each level of indentation.
	     * @return a printable, displayable, transmittable representation of the
	     *         object, beginning with <code>[</code>&nbsp;<small>(left
	     *         bracket)</small> and ending with <code>]</code>
	     *         &nbsp;<small>(right bracket)</small>.
	     * @throws JSONException
	     */
	    public String toString(int indentFactor) throws JSONException {
	        StringWriter sw = new StringWriter();
	        synchronized (sw.getBuffer()) {
	            return this.write(sw, indentFactor, 0).toString();
	        }
	    }

	    /**
	     * Write the contents of the JSONArray as JSON text to a writer. For
	     * compactness, no whitespace is added.
	     * <p><b>
	     * Warning: This method assumes that the data structure is acyclical.
	     *</b>
	     *
	     * @return The writer.
	     * @throws JSONException
	     */
	    public Writer write(Writer writer) throws JSONException {
	        return this.write(writer, 0, 0);
	    }

	    /**
	     * Write the contents of the JSONArray as JSON text to a writer.
	     * 
	     * <p>If <code>indentFactor > 0</code> and the {@link JSONArray} has only
	     * one element, then the array will be output on a single line:
	     * <pre>{@code [1]}</pre>
	     * 
	     * <p>If an array has 2 or more elements, then it will be output across
	     * multiple lines: <pre>{@code
	     * [
	     * 1,
	     * "value 2",
	     * 3
	     * ]
	     * }</pre>
	     * <p><b>
	     * Warning: This method assumes that the data structure is acyclical.
	     * </b>
	     *
	     * @param writer
	     *            Writes the serialized JSON
	     * @param indentFactor
	     *            The number of spaces to add to each level of indentation.
	     * @param indent
	     *            The indentation of the top level.
	     * @return The writer.
	     * @throws JSONException
	     */
	    public Writer write(Writer writer, int indentFactor, int indent)
	            throws JSONException {
	        try {
	            boolean commanate = false;
	            int length = this.length();
	            writer.write('[');

	            if (length == 1) {
	                try {
	                    writeValue(writer, this.myArrayList.get(0),
	                            indentFactor, indent);
	                } catch (Exception e) {
	                    throw new JSONException("Unable to write JSONArray value at index: 0", e);
	                }
	            } else if (length != 0) {
	                final int newindent = indent + indentFactor;

	                for (int i = 0; i < length; i += 1) {
	                    if (commanate) {
	                        writer.write(',');
	                    }
	                    if (indentFactor > 0) {
	                        writer.write('\n');
	                    }
	                    indent(writer, newindent);
	                    try {
	                        writeValue(writer, this.myArrayList.get(i),
	                                indentFactor, newindent);
	                    } catch (Exception e) {
	                        throw new JSONException("Unable to write JSONArray value at index: " + i, e);
	                    }
	                    commanate = true;
	                }
	                if (indentFactor > 0) {
	                    writer.write('\n');
	                }
	                indent(writer, indent);
	            }
	            writer.write(']');
	            return writer;
	        } catch (IOException e) {
	            throw new JSONException(e);
	        }
	    }

	    /**
	     * Returns a java.util.List containing all of the elements in this array.
	     * If an element in the array is a JSONArray or JSONObject it will also
	     * be converted.
	     * <p>
	     * Warning: This method assumes that the data structure is acyclical.
	     *
	     * @return a java.util.List containing the elements of this array
	     */
	    public List<Object> toList() {
	        List<Object> results = new ArrayList<Object>(this.myArrayList.size());
	        for (Object element : this.myArrayList) {
	            if (element == null || NULL.equals(element)) {
	                results.add(null);
	            } else if (element instanceof JSONArray) {
	                results.add(((JSONArray) element).toList());
	            } else if (element instanceof JSONObject) {
	                results.add(((JSONObject) element).toMap());
	            } else {
	                results.add(element);
	            }
	        }
	        return results;
	    }
	}

	/**
	 * The JSONException is thrown by the JSON.org classes when things are amiss.
	 *
	 * @author JSON.org
	 * @version 2015-12-09
	 */
	public static class JSONException extends RuntimeException {
	    /** Serialization ID */
	    private static final long serialVersionUID = 0;

	    /**
	     * Constructs a JSONException with an explanatory message.
	     *
	     * @param message
	     *            Detail about the reason for the exception.
	     */
	    public JSONException(final String message) {
	        super(message);
	    }

	    /**
	     * Constructs a JSONException with an explanatory message and cause.
	     * 
	     * @param message
	     *            Detail about the reason for the exception.
	     * @param cause
	     *            The cause.
	     */
	    public JSONException(final String message, final Throwable cause) {
	        super(message, cause);
	    }

	    /**
	     * Constructs a new JSONException with the specified cause.
	     * 
	     * @param cause
	     *            The cause.
	     */
	    public JSONException(final Throwable cause) {
	        super(cause.getMessage(), cause);
	    }

	}



	/**
	 * A JSONTokener takes a source string and extracts characters and tokens from
	 * it. It is used by the JSONObject and JSONArray constructors to parse
	 * JSON source strings.
	 * @author JSON.org
	 * @version 2014-05-03
	 */
	public static class JSONTokener {
	    /** current read character position on the current line. */
	    private long character;
	    /** flag to indicate if the end of the input has been found. */
	    private boolean eof;
	    /** current read index of the input. */
	    private long index;
	    /** current line of the input. */
	    private long line;
	    /** previous character read from the input. */
	    private char previous;
	    /** Reader for the input. */
	    private final Reader reader;
	    /** flag to indicate that a previous character was requested. */
	    private boolean usePrevious;
	    /** the number of characters read in the previous line. */
	    private long characterPreviousLine;


	    /**
	     * Construct a JSONTokener from a Reader. The caller must close the Reader.
	     *
	     * @param reader     A reader.
	     */
	    public JSONTokener(Reader reader) {
	        this.reader = reader.markSupported()
	                ? reader
	                        : new BufferedReader(reader);
	        this.eof = false;
	        this.usePrevious = false;
	        this.previous = 0;
	        this.index = 0;
	        this.character = 1;
	        this.characterPreviousLine = 0;
	        this.line = 1;
	    }


	    /**
	     * Construct a JSONTokener from an InputStream. The caller must close the input stream.
	     * @param inputStream The source.
	     */
	    public JSONTokener(InputStream inputStream) {
	        this(new InputStreamReader(inputStream));
	    }


	    /**
	     * Construct a JSONTokener from a string.
	     *
	     * @param s     A source string.
	     */
	    public JSONTokener(String s) {
	        this(new StringReader(s));
	    }


	    /**
	     * Back up one character. This provides a sort of lookahead capability,
	     * so that you can test for a digit or letter before attempting to parse
	     * the next number or identifier.
	     * @throws JSONException Thrown if trying to step back more than 1 step
	     *  or if already at the start of the string
	     */
	    public void back() throws JSONException {
	        if (this.usePrevious || this.index <= 0) {
	            throw new JSONException("Stepping back two steps is not supported");
	        }
	        this.decrementIndexes();
	        this.usePrevious = true;
	        this.eof = false;
	    }

	    /**
	     * Decrements the indexes for the {@link #back()} method based on the previous character read.
	     */
	    private void decrementIndexes() {
	        this.index--;
	        if(this.previous=='\r' || this.previous == '\n') {
	            this.line--;
	            this.character=this.characterPreviousLine ;
	        } else if(this.character > 0){
	            this.character--;
	        }
	    }

	    /**
	     * Get the hex value of a character (base16).
	     * @param c A character between '0' and '9' or between 'A' and 'F' or
	     * between 'a' and 'f'.
	     * @return  An int between 0 and 15, or -1 if c was not a hex digit.
	     */
	    public int dehexchar(char c) {
	        if (c >= '0' && c <= '9') {
	            return c - '0';
	        }
	        if (c >= 'A' && c <= 'F') {
	            return c - ('A' - 10);
	        }
	        if (c >= 'a' && c <= 'f') {
	            return c - ('a' - 10);
	        }
	        return -1;
	    }

	    /**
	     * Checks if the end of the input has been reached.
	     *  
	     * @return true if at the end of the file and we didn't step back
	     */
	    public boolean end() {
	        return this.eof && !this.usePrevious;
	    }


	    /**
	     * Determine if the source string still contains characters that next()
	     * can consume.
	     * @return true if not yet at the end of the source.
	     * @throws JSONException thrown if there is an error stepping forward
	     *  or backward while checking for more data.
	     */
	    public boolean more() throws JSONException {
	        if(this.usePrevious) {
	            return true;
	        }
	        try {
	            this.reader.mark(1);
	        } catch (IOException e) {
	            throw new JSONException("Unable to preserve stream position", e);
	        }
	        try {
	            // -1 is EOF, but next() can not consume the null character '\0'
	            if(this.reader.read() <= 0) {
	                this.eof = true;
	                return false;
	            }
	            this.reader.reset();
	        } catch (IOException e) {
	            throw new JSONException("Unable to read the next character from the stream", e);
	        }
	        return true;
	    }


	    /**
	     * Get the next character in the source string.
	     *
	     * @return The next character, or 0 if past the end of the source string.
	     * @throws JSONException Thrown if there is an error reading the source string.
	     */
	    public char next() throws JSONException {
	        int c;
	        if (this.usePrevious) {
	            this.usePrevious = false;
	            c = this.previous;
	        } else {
	            try {
	                c = this.reader.read();
	            } catch (IOException exception) {
	                throw new JSONException(exception);
	            }
	        }
	        if (c <= 0) { // End of stream
	            this.eof = true;
	            return 0;
	        }
	        this.incrementIndexes(c);
	        this.previous = (char) c;
	        return this.previous;
	    }

	    /**
	     * Increments the internal indexes according to the previous character
	     * read and the character passed as the current character.
	     * @param c the current character read.
	     */
	    private void incrementIndexes(int c) {
	        if(c > 0) {
	            this.index++;
	            if(c=='\r') {
	                this.line++;
	                this.characterPreviousLine = this.character;
	                this.character=0;
	            }else if (c=='\n') {
	                if(this.previous != '\r') {
	                    this.line++;
	                    this.characterPreviousLine = this.character;
	                }
	                this.character=0;
	            } else {
	                this.character++;
	            }
	        }
	    }

	    /**
	     * Consume the next character, and check that it matches a specified
	     * character.
	     * @param c The character to match.
	     * @return The character.
	     * @throws JSONException if the character does not match.
	     */
	    public char next(char c) throws JSONException {
	        char n = this.next();
	        if (n != c) {
	            if(n > 0) {
	                throw this.syntaxError("Expected '" + c + "' and instead saw '" +
	                        n + "'");
	            }
	            throw this.syntaxError("Expected '" + c + "' and instead saw ''");
	        }
	        return n;
	    }


	    /**
	     * Get the next n characters.
	     *
	     * @param n     The number of characters to take.
	     * @return      A string of n characters.
	     * @throws JSONException
	     *   Substring bounds error if there are not
	     *   n characters remaining in the source string.
	     */
	    public String next(int n) throws JSONException {
	        if (n == 0) {
	            return "";
	        }

	        char[] chars = new char[n];
	        int pos = 0;

	        while (pos < n) {
	            chars[pos] = this.next();
	            if (this.end()) {
	                throw this.syntaxError("Substring bounds error");
	            }
	            pos += 1;
	        }
	        return new String(chars);
	    }


	    /**
	     * Get the next char in the string, skipping whitespace.
	     * @throws JSONException Thrown if there is an error reading the source string.
	     * @return  A character, or 0 if there are no more characters.
	     */
	    public char nextClean() throws JSONException {
	        for (;;) {
	            char c = this.next();
	            if (c == 0 || c > ' ') {
	                return c;
	            }
	        }
	    }


	    /**
	     * Return the characters up to the next close quote character.
	     * Backslash processing is done. The formal JSON format does not
	     * allow strings in single quotes, but an implementation is allowed to
	     * accept them.
	     * @param quote The quoting character, either
	     *      <code>"</code>&nbsp;<small>(double quote)</small> or
	     *      <code>'</code>&nbsp;<small>(single quote)</small>.
	     * @return      A String.
	     * @throws JSONException Unterminated string.
	     */
	    public String nextString(char quote) throws JSONException {
	        char c;
	        StringBuilder sb = new StringBuilder();
	        for (;;) {
	            c = this.next();
	            switch (c) {
	            case 0:
	            case '\n':
	            case '\r':
	                throw this.syntaxError("Unterminated string");
	            case '\\':
	                c = this.next();
	                switch (c) {
	                case 'b':
	                    sb.append('\b');
	                    break;
	                case 't':
	                    sb.append('\t');
	                    break;
	                case 'n':
	                    sb.append('\n');
	                    break;
	                case 'f':
	                    sb.append('\f');
	                    break;
	                case 'r':
	                    sb.append('\r');
	                    break;
	                case 'u':
	                    try {
	                        sb.append((char)Integer.parseInt(this.next(4), 16));
	                    } catch (NumberFormatException e) {
	                        throw this.syntaxError("Illegal escape.", e);
	                    }
	                    break;
	                case '"':
	                case '\'':
	                case '\\':
	                case '/':
	                    sb.append(c);
	                    break;
	                default:
	                    throw this.syntaxError("Illegal escape.");
	                }
	                break;
	            default:
	                if (c == quote) {
	                    return sb.toString();
	                }
	                sb.append(c);
	            }
	        }
	    }


	    /**
	     * Get the text up but not including the specified character or the
	     * end of line, whichever comes first.
	     * @param  delimiter A delimiter character.
	     * @return   A string.
	     * @throws JSONException Thrown if there is an error while searching
	     *  for the delimiter
	     */
	    public String nextTo(char delimiter) throws JSONException {
	        StringBuilder sb = new StringBuilder();
	        for (;;) {
	            char c = this.next();
	            if (c == delimiter || c == 0 || c == '\n' || c == '\r') {
	                if (c != 0) {
	                    this.back();
	                }
	                return sb.toString().trim();
	            }
	            sb.append(c);
	        }
	    }


	    /**
	     * Get the text up but not including one of the specified delimiter
	     * characters or the end of line, whichever comes first.
	     * @param delimiters A set of delimiter characters.
	     * @return A string, trimmed.
	     * @throws JSONException Thrown if there is an error while searching
	     *  for the delimiter
	     */
	    public String nextTo(String delimiters) throws JSONException {
	        char c;
	        StringBuilder sb = new StringBuilder();
	        for (;;) {
	            c = this.next();
	            if (delimiters.indexOf(c) >= 0 || c == 0 ||
	                    c == '\n' || c == '\r') {
	                if (c != 0) {
	                    this.back();
	                }
	                return sb.toString().trim();
	            }
	            sb.append(c);
	        }
	    }


	    /**
	     * Get the next value. The value can be a Boolean, Double, Integer,
	     * JSONArray, JSONObject, Long, or String, or the NULL object.
	     * @throws JSONException If syntax error.
	     *
	     * @return An object.
	     */
	    public Object nextValue() throws JSONException {
	        char c = this.nextClean();
	        String string;

	        switch (c) {
	        case '"':
	        case '\'':
	            return this.nextString(c);
	        case '{':
	            this.back();
	            return new JSONObject(this);
	        case '[':
	            this.back();
	            return new JSONArray(this);
	        }

	        /*
	         * Handle unquoted text. This could be the values true, false, or
	         * null, or it can be a number. An implementation (such as this one)
	         * is allowed to also accept non-standard forms.
	         *
	         * Accumulate characters until we reach the end of the text or a
	         * formatting character.
	         */

	        StringBuilder sb = new StringBuilder();
	        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
	            sb.append(c);
	            c = this.next();
	        }
	        this.back();

	        string = sb.toString().trim();
	        if ("".equals(string)) {
	            throw this.syntaxError("Missing value");
	        }
	        return stringToValue(string);
	    }


	    /**
	     * Skip characters until the next character is the requested character.
	     * If the requested character is not found, no characters are skipped.
	     * @param to A character to skip to.
	     * @return The requested character, or zero if the requested character
	     * is not found.
	     * @throws JSONException Thrown if there is an error while searching
	     *  for the to character
	     */
	    public char skipTo(char to) throws JSONException {
	        char c;
	        try {
	            long startIndex = this.index;
	            long startCharacter = this.character;
	            long startLine = this.line;
	            this.reader.mark(1000000);
	            do {
	                c = this.next();
	                if (c == 0) {
	                    // in some readers, reset() may throw an exception if
	                    // the remaining portion of the input is greater than
	                    // the mark size (1,000,000 above).
	                    this.reader.reset();
	                    this.index = startIndex;
	                    this.character = startCharacter;
	                    this.line = startLine;
	                    return 0;
	                }
	            } while (c != to);
	            this.reader.mark(1);
	        } catch (IOException exception) {
	            throw new JSONException(exception);
	        }
	        this.back();
	        return c;
	    }

	    /**
	     * Make a JSONException to signal a syntax error.
	     *
	     * @param message The error message.
	     * @return  A JSONException object, suitable for throwing
	     */
	    public JSONException syntaxError(String message) {
	        return new JSONException(message + this.toString());
	    }

	    /**
	     * Make a JSONException to signal a syntax error.
	     *
	     * @param message The error message.
	     * @param causedBy The throwable that caused the error.
	     * @return  A JSONException object, suitable for throwing
	     */
	    public JSONException syntaxError(String message, Throwable causedBy) {
	        return new JSONException(message + this.toString(), causedBy);
	    }

	    /**
	     * Make a printable string of this JSONTokener.
	     *
	     * @return " at {index} [character {character} line {line}]"
	     */
	    @Override
	    public String toString() {
	        return " at " + this.index + " [character " + this.character + " line " +
	                this.line + "]";
	    }
	}

	/**
	 * The XMLTokener extends the JSONTokener to provide additional methods
	 * for the parsing of XML texts.
	 * @author JSON.org
	 * @version 2015-12-09
	 */
	
	   public static final Character AMP = '&';

	    /** The Character '''. */
	    public static final Character APOS = '\'';

	    /** The Character '!'. */
	    public static final Character BANG = '!';

	    /** The Character '='. */
	    public static final Character EQ = '=';

	    /** The Character '>'. */
	    public static final Character GT = '>';

	    /** The Character '&lt;'. */
	    public static final Character LT = '<';

	    /** The Character '?'. */
	    public static final Character QUEST = '?';

	    /** The Character '"'. */
	    public static final Character QUOT = '"';

	    /** The Character '/'. */
	    public static final Character SLASH = '/';
	    
	    
	/** The table of entity values. It initially contains Character values for
	 * amp, apos, gt, lt, quot.
	 */
	public static final java.util.HashMap<String, Character> entity;

	static {
		entity = new java.util.HashMap<String, Character>(8);
		entity.put("amp",  AMP);
		entity.put("apos", APOS);
		entity.put("gt",   GT);
		entity.put("lt",   LT);
		entity.put("quot", QUOT);
	}
	  
    /**
     * Unescapes an XML entity encoding;
     * @param e entity (only the actual entity value, not the preceding & or ending ;
     * @return
     */
    static String unescapeEntity(String e) {
        // validate
        if (e == null || e.isEmpty()) {
            return "";
        }
        // if our entity is an encoded unicode point, parse it.
        if (e.charAt(0) == '#') {
            int cp;
            if (e.charAt(1) == 'x') {
                // hex encoded unicode
                cp = Integer.parseInt(e.substring(2), 16);
            } else {
                // decimal encoded unicode
                cp = Integer.parseInt(e.substring(1));
            }
            return new String(new int[] {cp},0,1);
        } 
        Character knownEntity = entity.get(e);
        if(knownEntity==null) {
            // we don't know the entity so keep it encoded
            return '&' + e + ';';
        }
        return knownEntity.toString();
    }

    
	public static class XMLTokener extends JSONTokener {




	    /**
	     * Construct an XMLTokener from a string.
	     * @param s A source string.
	     */
	    public XMLTokener(String s) {
	        super(s);
	    }

	    /**
	     * Get the text in the CDATA block.
	     * @return The string up to the <code>]]&gt;</code>.
	     * @throws JSONException If the <code>]]&gt;</code> is not found.
	     */
	    public String nextCDATA() throws JSONException {
	        char         c;
	        int          i;
	        StringBuilder sb = new StringBuilder();
	        while (more()) {
	            c = next();
	            sb.append(c);
	            i = sb.length() - 3;
	            if (i >= 0 && sb.charAt(i) == ']' &&
	                          sb.charAt(i + 1) == ']' && sb.charAt(i + 2) == '>') {
	                sb.setLength(i);
	                return sb.toString();
	            }
	        }
	        throw syntaxError("Unclosed CDATA");
	    }


	    /**
	     * Get the next XML outer token, trimming whitespace. There are two kinds
	     * of tokens: the '<' character which begins a markup tag, and the content
	     * text between markup tags.
	     *
	     * @return  A string, or a '<' Character, or null if there is no more
	     * source text.
	     * @throws JSONException
	     */
	    public Object nextContent() throws JSONException {
	        char         c;
	        StringBuilder sb;
	        do {
	            c = next();
	        } while (Character.isWhitespace(c));
	        if (c == 0) {
	            return null;
	        }
	        if (c == '<') {
	            return JSONJava.LT;
	        }
	        sb = new StringBuilder();
	        for (;;) {
	            if (c == 0) {
	                return sb.toString().trim();
	            }
	            if (c == '<') {
	                back();
	                return sb.toString().trim();
	            }
	            if (c == '&') {
	                sb.append(nextEntity(c));
	            } else {
	                sb.append(c);
	            }
	            c = next();
	        }
	    }


	    /**
	     * Return the next entity. These entities are translated to Characters:
	     *     <code>&amp;  &apos;  &gt;  &lt;  &quot;</code>.
	     * @param ampersand An ampersand character.
	     * @return  A Character or an entity String if the entity is not recognized.
	     * @throws JSONException If missing ';' in XML entity.
	     */
	    public Object nextEntity(char ampersand) throws JSONException {
	        StringBuilder sb = new StringBuilder();
	        for (;;) {
	            char c = next();
	            if (Character.isLetterOrDigit(c) || c == '#') {
	                sb.append(Character.toLowerCase(c));
	            } else if (c == ';') {
	                break;
	            } else {
	                throw syntaxError("Missing ';' in XML entity: &" + sb);
	            }
	        }
	        String string = sb.toString();
	        return unescapeEntity(string);
	    }
	    

	    /**
	     * Returns the next XML meta token. This is used for skipping over <!...>
	     * and <?...?> structures.
	     * @return Syntax characters (<code>< > / = ! ?</code>) are returned as
	     *  Character, and strings and names are returned as Boolean. We don't care
	     *  what the values actually are.
	     * @throws JSONException If a string is not properly closed or if the XML
	     *  is badly structured.
	     */
	    public Object nextMeta() throws JSONException {
	        char c;
	        char q;
	        do {
	            c = next();
	        } while (Character.isWhitespace(c));
	        switch (c) {
	        case 0:
	            throw syntaxError("Misshaped meta tag");
	        case '<':
	            return JSONJava.LT;
	        case '>':
	            return JSONJava.GT;
	        case '/':
	            return JSONJava.SLASH;
	        case '=':
	            return JSONJava.EQ;
	        case '!':
	            return JSONJava.BANG;
	        case '?':
	            return JSONJava.QUEST;
	        case '"':
	        case '\'':
	            q = c;
	            for (;;) {
	                c = next();
	                if (c == 0) {
	                    throw syntaxError("Unterminated string");
	                }
	                if (c == q) {
	                    return Boolean.TRUE;
	                }
	            }
	        default:
	            for (;;) {
	                c = next();
	                if (Character.isWhitespace(c)) {
	                    return Boolean.TRUE;
	                }
	                switch (c) {
	                case 0:
	                case '<':
	                case '>':
	                case '/':
	                case '=':
	                case '!':
	                case '?':
	                case '"':
	                case '\'':
	                    back();
	                    return Boolean.TRUE;
	                }
	            }
	        }
	    }


	    /**
	     * Get the next XML Token. These tokens are found inside of angle
	     * brackets. It may be one of these characters: <code>/ > = ! ?</code> or it
	     * may be a string wrapped in single quotes or double quotes, or it may be a
	     * name.
	     * @return a String or a Character.
	     * @throws JSONException If the XML is not well formed.
	     */
	    public Object nextToken() throws JSONException {
	        char c;
	        char q;
	        StringBuilder sb;
	        do {
	            c = next();
	        } while (Character.isWhitespace(c));
	        switch (c) {
	        case 0:
	            throw syntaxError("Misshaped element");
	        case '<':
	            throw syntaxError("Misplaced '<'");
	        case '>':
	            return JSONJava.GT;
	        case '/':
	            return JSONJava.SLASH;
	        case '=':
	            return JSONJava.EQ;
	        case '!':
	            return JSONJava.BANG;
	        case '?':
	            return JSONJava.QUEST;

	// Quoted string

	        case '"':
	        case '\'':
	            q = c;
	            sb = new StringBuilder();
	            for (;;) {
	                c = next();
	                if (c == 0) {
	                    throw syntaxError("Unterminated string");
	                }
	                if (c == q) {
	                    return sb.toString();
	                }
	                if (c == '&') {
	                    sb.append(nextEntity(c));
	                } else {
	                    sb.append(c);
	                }
	            }
	        default:

	// Name

	            sb = new StringBuilder();
	            for (;;) {
	                sb.append(c);
	                c = next();
	                if (Character.isWhitespace(c)) {
	                    return sb.toString();
	                }
	                switch (c) {
	                case 0:
	                    return sb.toString();
	                case '>':
	                case '/':
	                case '=':
	                case '!':
	                case '?':
	                case '[':
	                case ']':
	                    back();
	                    return sb.toString();
	                case '<':
	                case '"':
	                case '\'':
	                    throw syntaxError("Bad character in a name");
	                }
	            }
	        }
	    }


	    /**
	     * Skip characters until past the requested string.
	     * If it is not found, we are left at the end of the source with a result of false.
	     * @param to A string to skip past.
	     */
	    // The Android implementation of JSONTokener has a public method of public void skipPast(String to)
	    // even though ours does not have that method, to have API compatibility, our method in the subclass
	    // should match.
	    public void skipPast(String to) {
	        boolean b;
	        char c;
	        int i;
	        int j;
	        int offset = 0;
	        int length = to.length();
	        char[] circle = new char[length];

	        /*
	         * First fill the circle buffer with as many characters as are in the
	         * to string. If we reach an early end, bail.
	         */

	        for (i = 0; i < length; i += 1) {
	            c = next();
	            if (c == 0) {
	                return;
	            }
	            circle[i] = c;
	        }

	        /* We will loop, possibly for all of the remaining characters. */

	        for (;;) {
	            j = offset;
	            b = true;

	            /* Compare the circle buffer with the to string. */

	            for (i = 0; i < length; i += 1) {
	                if (circle[j] != to.charAt(i)) {
	                    b = false;
	                    break;
	                }
	                j += 1;
	                if (j >= length) {
	                    j -= length;
	                }
	            }

	            /* If we exit the loop with b intact, then victory is ours. */

	            if (b) {
	                return;
	            }

	            /* Get the next character. If there isn't one, then defeat is ours. */

	            c = next();
	            if (c == 0) {
	                return;
	            }
	            /*
	             * Shove the character in the circle buffer and advance the
	             * circle offset. The offset is mod n.
	             */
	            circle[offset] = c;
	            offset += 1;
	            if (offset >= length) {
	                offset -= length;
	            }
	        }
	    }
	}

	/**
	 * This provides static methods to convert an XML text into a JSONObject, and to
	 * covert a JSONObject into an XML text.
	 * 
	 * @author JSON.org
	 * @version 2016-08-10
	 */
	@SuppressWarnings("boxing")
	public static class XML {
	    /** The Character '&amp;'. */

	    
	    /**
	     * Creates an iterator for navigating Code Points in a string instead of
	     * characters. Once Java7 support is dropped, this can be replaced with
	     * <code>
	     * string.codePoints()
	     * </code>
	     * which is available in Java8 and above.
	     * 
	     * @see <a href=
	     *      "http://stackoverflow.com/a/21791059/6030888">http://stackoverflow.com/a/21791059/6030888</a>
	     */
	    private Iterable<Integer> codePointIterator(final String string) {
	        return new Iterable<Integer>() {
	            @Override
	            public Iterator<Integer> iterator() {
	                return new Iterator<Integer>() {
	                    private int nextIndex = 0;
	                    private int length = string.length();

	                    @Override
	                    public boolean hasNext() {
	                        return this.nextIndex < this.length;
	                    }

	                    @Override
	                    public Integer next() {
	                        int result = string.codePointAt(this.nextIndex);
	                        this.nextIndex += Character.charCount(result);
	                        return result;
	                    }

	                    @Override
	                    public void remove() {
	                        throw new UnsupportedOperationException();
	                    }
	                };
	            }
	        };
	    }

	    /**
	     * Replace special characters with XML escapes:
	     * 
	     * <pre>
	     * &amp; <small>(ampersand)</small> is replaced by &amp;amp;
	     * &lt; <small>(less than)</small> is replaced by &amp;lt;
	     * &gt; <small>(greater than)</small> is replaced by &amp;gt;
	     * &quot; <small>(double quote)</small> is replaced by &amp;quot;
	     * &apos; <small>(single quote / apostrophe)</small> is replaced by &amp;apos;
	     * </pre>
	     * 
	     * @param string
	     *            The string to be escaped.
	     * @return The escaped string.
	     */
	    public String escape(String string) {
	        StringBuilder sb = new StringBuilder(string.length());
	        for (final int cp : codePointIterator(string)) {
	            switch (cp) {
	            case '&':
	                sb.append("&amp;");
	                break;
	            case '<':
	                sb.append("&lt;");
	                break;
	            case '>':
	                sb.append("&gt;");
	                break;
	            case '"':
	                sb.append("&quot;");
	                break;
	            case '\'':
	                sb.append("&apos;");
	                break;
	            default:
	                if (mustEscape(cp)) {
	                    sb.append("&#x");
	                    sb.append(Integer.toHexString(cp));
	                    sb.append(';');
	                } else {
	                    sb.appendCodePoint(cp);
	                }
	            }
	        }
	        return sb.toString();
	    }
	    
	    /**
	     * @param cp code point to test
	     * @return true if the code point is not valid for an XML
	     */
	    private boolean mustEscape(int cp) {
	        /* Valid range from https://www.w3.org/TR/REC-xml/#charsets
	         * 
	         * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF] 
	         * 
	         * any Unicode character, excluding the surrogate blocks, FFFE, and FFFF. 
	         */
	        // isISOControl is true when (cp >= 0 && cp <= 0x1F) || (cp >= 0x7F && cp <= 0x9F)
	        // all ISO control characters are out of range except tabs and new lines
	        return (Character.isISOControl(cp)
	                && cp != 0x9
	                && cp != 0xA
	                && cp != 0xD
	            ) || !(
	                // valid the range of acceptable characters that aren't control
	                (cp >= 0x20 && cp <= 0xD7FF)
	                || (cp >= 0xE000 && cp <= 0xFFFD)
	                || (cp >= 0x10000 && cp <= 0x10FFFF)
	            )
	        ;
	    }

	    /**
	     * Removes XML escapes from the string.
	     * 
	     * @param string
	     *            string to remove escapes from
	     * @return string with converted entities
	     */
	    public String unescape(String string) {
	        StringBuilder sb = new StringBuilder(string.length());
	        for (int i = 0, length = string.length(); i < length; i++) {
	            char c = string.charAt(i);
	            if (c == '&') {
	                final int semic = string.indexOf(';', i);
	                if (semic > i) {
	                    final String entity = string.substring(i + 1, semic);
	                    sb.append(unescapeEntity(entity));
	                    // skip past the entity we just parsed.
	                    i += entity.length() + 1;
	                } else {
	                    // this shouldn't happen in most cases since the parser
	                    // errors on unclosed entries.
	                    sb.append(c);
	                }
	            } else {
	                // not part of an entity
	                sb.append(c);
	            }
	        }
	        return sb.toString();
	    }

	    /**
	     * Throw an exception if the string contains whitespace. Whitespace is not
	     * allowed in tagNames and attributes.
	     * 
	     * @param string
	     *            A string.
	     * @throws JSONException Thrown if the string contains whitespace or is empty.
	     */
	    public void noSpace(String string) throws JSONException {
	        int i, length = string.length();
	        if (length == 0) {
	            throw new JSONException("Empty string.");
	        }
	        for (i = 0; i < length; i += 1) {
	            if (Character.isWhitespace(string.charAt(i))) {
	                throw new JSONException("'" + string
	                        + "' contains a space character.");
	            }
	        }
	    }

	    /**
	     * Scan the content following the named tag, attaching it to the context.
	     * 
	     * @param x
	     *            The XMLTokener containing the source string.
	     * @param context
	     *            The JSONObject that will include the new material.
	     * @param name
	     *            The tag name.
	     * @return true if the close tag is processed.
	     * @throws JSONException
	     */
	    private boolean parse(XMLTokener x, JSONObject context, String name, boolean keepStrings)
	            throws JSONException {
	        char c;
	        int i;
	        JSONObject jsonobject = null;
	        String string;
	        String tagName;
	        Object token;

	        // Test for and skip past these forms:
	        // <!-- ... -->
	        // <! ... >
	        // <![ ... ]]>
	        // <? ... ?>
	        // Report errors for these forms:
	        // <>
	        // <=
	        // <<

	        token = x.nextToken();

	        // <!

	        if (token == BANG) {
	            c = x.next();
	            if (c == '-') {
	                if (x.next() == '-') {
	                    x.skipPast("-->");
	                    return false;
	                }
	                x.back();
	            } else if (c == '[') {
	                token = x.nextToken();
	                if ("CDATA".equals(token)) {
	                    if (x.next() == '[') {
	                        string = x.nextCDATA();
	                        if (string.length() > 0) {
	                            context.accumulate("content", string);
	                        }
	                        return false;
	                    }
	                }
	                throw x.syntaxError("Expected 'CDATA['");
	            }
	            i = 1;
	            do {
	                token = x.nextMeta();
	                if (token == null) {
	                    throw x.syntaxError("Missing '>' after '<!'.");
	                } else if (token == LT) {
	                    i += 1;
	                } else if (token == GT) {
	                    i -= 1;
	                }
	            } while (i > 0);
	            return false;
	        } else if (token == QUEST) {

	            // <?
	            x.skipPast("?>");
	            return false;
	        } else if (token == SLASH) {

	            // Close tag </

	            token = x.nextToken();
	            if (name == null) {
	                throw x.syntaxError("Mismatched close tag " + token);
	            }
	            if (!token.equals(name)) {
	                throw x.syntaxError("Mismatched " + name + " and " + token);
	            }
	            if (x.nextToken() != GT) {
	                throw x.syntaxError("Misshaped close tag");
	            }
	            return true;

	        } else if (token instanceof Character) {
	            throw x.syntaxError("Misshaped tag");

	            // Open tag <

	        } else {
	            tagName = (String) token;
	            token = null;
	            jsonobject = new JSONObject();
	            for (;;) {
	                if (token == null) {
	                    token = x.nextToken();
	                }
	                // attribute = value
	                if (token instanceof String) {
	                    string = (String) token;
	                    token = x.nextToken();
	                    if (token == EQ) {
	                        token = x.nextToken();
	                        if (!(token instanceof String)) {
	                            throw x.syntaxError("Missing value");
	                        }
	                        jsonobject.accumulate(string,
	                                keepStrings ? ((String)token) : stringToValue((String) token));
	                        token = null;
	                    } else {
	                        jsonobject.accumulate(string, "");
	                    }


	                } else if (token == SLASH) {
	                    // Empty tag <.../>
	                    if (x.nextToken() != GT) {
	                        throw x.syntaxError("Misshaped tag");
	                    }
	                    if (jsonobject.length() > 0) {
	                        context.accumulate(tagName, jsonobject);
	                    } else {
	                        context.accumulate(tagName, "");
	                    }
	                    return false;

	                } else if (token == GT) {
	                    // Content, between <...> and </...>
	                    for (;;) {
	                        token = x.nextContent();
	                        if (token == null) {
	                            if (tagName != null) {
	                                throw x.syntaxError("Unclosed tag " + tagName);
	                            }
	                            return false;
	                        } else if (token instanceof String) {
	                            string = (String) token;
	                            if (string.length() > 0) {
	                                jsonobject.accumulate("content",
	                                        keepStrings ? string : stringToValue(string));
	                            }

	                        } else if (token == LT) {
	                            // Nested element
	                            if (parse(x, jsonobject, tagName,keepStrings)) {
	                                if (jsonobject.length() == 0) {
	                                    context.accumulate(tagName, "");
	                                } else if (jsonobject.length() == 1
	                                        && jsonobject.opt("content") != null) {
	                                    context.accumulate(tagName,
	                                            jsonobject.opt("content"));
	                                } else {
	                                    context.accumulate(tagName, jsonobject);
	                                }
	                                return false;
	                            }
	                        }
	                    }
	                } else {
	                    throw x.syntaxError("Misshaped tag");
	                }
	            }
	        }
	    }
	    
	    /**
	     * This method is the same as {@link JSONObject#stringToValue(String)}.
	     * 
	     * @param string String to convert
	     * @return JSON value of this string or the string
	     */
	    // To maintain compatibility with the Android API, this method is a direct copy of
	    // the one in JSONObject. Changes made here should be reflected there.
	    public Object stringToValue(String string) {
	        if (string.equals("")) {
	            return string;
	        }
	        if (string.equalsIgnoreCase("true")) {
	            return Boolean.TRUE;
	        }
	        if (string.equalsIgnoreCase("false")) {
	            return Boolean.FALSE;
	        }
	        if (string.equalsIgnoreCase("null")) {
	            return NULL;
	        }

	        /*
	         * If it might be a number, try converting it. If a number cannot be
	         * produced, then the value will just be a string.
	         */

	        char initial = string.charAt(0);
	        if ((initial >= '0' && initial <= '9') || initial == '-') {
	            try {
	                // if we want full Big Number support this block can be replaced with:
	                // return stringToNumber(string);
	                if (string.indexOf('.') > -1 || string.indexOf('e') > -1
	                        || string.indexOf('E') > -1 || "-0".equals(string)) {
	                    Double d = Double.valueOf(string);
	                    if (!d.isInfinite() && !d.isNaN()) {
	                        return d;
	                    }
	                } else {
	                    Long myLong = Long.valueOf(string);
	                    if (string.equals(myLong.toString())) {
	                        if (myLong.longValue() == myLong.intValue()) {
	                            return Integer.valueOf(myLong.intValue());
	                        }
	                        return myLong;
	                    }
	                }
	            } catch (Exception ignore) {
	            }
	        }
	        return string;
	    }

	    /**
	     * Convert a well-formed (but not necessarily valid) XML string into a
	     * JSONObject. Some information may be lost in this transformation because
	     * JSON is a data format and XML is a document format. XML uses elements,
	     * attributes, and content text, while JSON uses unordered collections of
	     * name/value pairs and arrays of values. JSON does not does not like to
	     * distinguish between elements and attributes. Sequences of similar
	     * elements are represented as JSONArrays. Content text may be placed in a
	     * "content" member. Comments, prologs, DTDs, and <code>&lt;[ [ ]]></code>
	     * are ignored.
	     * 
	     * @param string
	     *            The source string.
	     * @return A JSONObject containing the structured data from the XML string.
	     * @throws JSONException Thrown if there is an errors while parsing the string
	     */
	    public JSONObject toJSONObject(String string) throws JSONException {
	        return toJSONObject(string, false);
	    }


	    /**
	     * Convert a well-formed (but not necessarily valid) XML string into a
	     * JSONObject. Some information may be lost in this transformation because
	     * JSON is a data format and XML is a document format. XML uses elements,
	     * attributes, and content text, while JSON uses unordered collections of
	     * name/value pairs and arrays of values. JSON does not does not like to
	     * distinguish between elements and attributes. Sequences of similar
	     * elements are represented as JSONArrays. Content text may be placed in a
	     * "content" member. Comments, prologs, DTDs, and <code>&lt;[ [ ]]></code>
	     * are ignored.
	     * 
	     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
	     * numbers but will instead be the exact value as seen in the XML document.
	     * 
	     * @param string
	     *            The source string.
	     * @param keepStrings If true, then values will not be coerced into boolean
	     *  or numeric values and will instead be left as strings
	     * @return A JSONObject containing the structured data from the XML string.
	     * @throws JSONException Thrown if there is an errors while parsing the string
	     */
	    public JSONObject toJSONObject(String string, boolean keepStrings) throws JSONException {
	        JSONObject jo = new JSONObject();
	        XMLTokener x = new XMLTokener(string);
	        while (x.more()) {
	        	x.skipPast("<");
	        	if(x.more()) {
	        		parse(x, jo, null, keepStrings);
	        	}
	        }
	        return jo;
	    }
	    /**
	     * Convert a JSONObject into a well-formed, element-normal XML string.
	     * 
	     * @param object
	     *            A JSONObject.
	     * @return A string.
	     * @throws JSONException Thrown if there is an error parsing the string
	     */
	    public String toString(Object object) throws JSONException {
	        return toString(object, null);
	    }

	    /**
	     * Convert a JSONObject into a well-formed, element-normal XML string.
	     * 
	     * @param object
	     *            A JSONObject.
	     * @param tagName
	     *            The optional name of the enclosing tag.
	     * @return A string.
	     * @throws JSONException Thrown if there is an error parsing the string
	     */
	    public String toString(final Object object, final String tagName)
	            throws JSONException {
	        StringBuilder sb = new StringBuilder();
	        JSONArray ja;
	        JSONObject jo;
	        String string;

	        if (object instanceof JSONObject) {

	            // Emit <tagName>
	            if (tagName != null) {
	                sb.append('<');
	                sb.append(tagName);
	                sb.append('>');
	            }

	            // Loop thru the keys.
	            // don't use the new entrySet accessor to maintain Android Support
	            jo = (JSONObject) object;
	            for (final String key : jo.keySet()) {
	                Object value = jo.opt(key);
	                if (value == null) {
	                    value = "";
	                } else if (value.getClass().isArray()) {
	                    value = new JSONArray(value);
	                }

	                // Emit content in body
	                if ("content".equals(key)) {
	                    if (value instanceof JSONArray) {
	                        ja = (JSONArray) value;
	                        int jaLength = ja.length();
	                        // don't use the new iterator API to maintain support for Android
							for (int i = 0; i < jaLength; i++) {
	                            if (i > 0) {
	                                sb.append('\n');
	                            }
	                            Object val = ja.opt(i);
	                            sb.append(escape(val.toString()));
	                        }
	                    } else {
	                        sb.append(escape(value.toString()));
	                    }

	                    // Emit an array of similar keys

	                } else if (value instanceof JSONArray) {
	                    ja = (JSONArray) value;
	                    int jaLength = ja.length();
	                    // don't use the new iterator API to maintain support for Android
						for (int i = 0; i < jaLength; i++) {
	                        Object val = ja.opt(i);
	                        if (val instanceof JSONArray) {
	                            sb.append('<');
	                            sb.append(key);
	                            sb.append('>');
	                            sb.append(toString(val));
	                            sb.append("</");
	                            sb.append(key);
	                            sb.append('>');
	                        } else {
	                            sb.append(toString(val, key));
	                        }
	                    }
	                } else if ("".equals(value)) {
	                    sb.append('<');
	                    sb.append(key);
	                    sb.append("/>");

	                    // Emit a new tag <k>

	                } else {
	                    sb.append(toString(value, key));
	                }
	            }
	            if (tagName != null) {

	                // Emit the </tagname> close tag
	                sb.append("</");
	                sb.append(tagName);
	                sb.append('>');
	            }
	            return sb.toString();

	        }

	        if (object != null && (object instanceof JSONArray ||  object.getClass().isArray())) {
	            if(object.getClass().isArray()) {
	                ja = new JSONArray(object);
	            } else {
	                ja = (JSONArray) object;
	            }
	            int jaLength = ja.length();
	            // don't use the new iterator API to maintain support for Android
				for (int i = 0; i < jaLength; i++) {
	                Object val = ja.opt(i);
	                // XML does not have good support for arrays. If an array
	                // appears in a place where XML is lacking, synthesize an
	                // <array> element.
	                sb.append(toString(val, tagName == null ? "array" : tagName));
	            }
	            return sb.toString();
	        }

	        string = (object == null) ? "null" : escape(object.toString());
	        return (tagName == null) ? "\"" + string + "\""
	                : (string.length() == 0) ? "<" + tagName + "/>" : "<" + tagName
	                        + ">" + string + "</" + tagName + ">";

	    }
	}

}
