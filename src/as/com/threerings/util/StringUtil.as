//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2007 Three Rings Design, Inc., All Rights Reserved
// http://www.threerings.net/code/narya/
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.util {

import flash.utils.ByteArray;
import flash.utils.Dictionary;

import com.threerings.util.env.Environment;

public class StringUtil
{
    /**
     * Get a reasonable hash code for the specified String.
     */
    public static function hashCode (str :String) :int
    {
        var code :int = 0;
        if (str != null) {
            // sample at most 8 chars
            var lastChar :int = Math.min(8, str.length);
            for (var ii :int = 0; ii < lastChar; ii++) {
                code = code * 31 + str.charCodeAt(ii);
            }
        }
        return code;
    }

    /**
     * Is the specified string null or does it contain only whitespace?
     */
    public static function isBlank (str :String) :Boolean
    {
        return (str == null) || (str.search("\\S") == -1);
    }

    /**
     * Does the specified string end with the specified substring.
     */
    public static function endsWith (str :String, substr :String) :Boolean
    {
        var startDex :int = str.length - substr.length;
        return (startDex >= 0) && (str.indexOf(substr, startDex) >= 0);
    }

    /**
     * Does the specified string start with the specified substring.
     */
    public static function startsWith (str :String, substr :String) :Boolean
    {
        // just check once if it's at the beginning
        return (str.lastIndexOf(substr, 0) == 0);
    }

    /**
     * Return true iff the first character is a lower-case character.
     */
    public static function isLowerCase (str :String) :Boolean
    {
        var firstChar :String = str.charAt(0);
        return (firstChar.toUpperCase() != firstChar) &&
            (firstChar.toLowerCase() == firstChar);
    }

    /**
     * Return true iff the first character is an upper-case character.
     */
    public static function isUpperCase (str :String) :Boolean
    {
        var firstChar :String = str.charAt(0);
        return (firstChar.toUpperCase() == firstChar) &&
            (firstChar.toLowerCase() != firstChar);
    }

    /**
     * Parse an integer more anally than the built-in parseInt() function,
     * throwing an ArgumentError if there are any invalid characters.
     *
     * The built-in parseInt() will ignore trailing non-integer characters.
     *
     * @param str The string to parse.
     * @param radix The radix to use, from 2 to 16. If not specified the radix will be 10,
     *        unless the String begins with "0x" in which case it will be 16,
     *        or the String begins with "0" in which case it will be 8.
     */
    public static function parseInteger (str :String, radix :uint = 0) :int
    {
        return int(parseInt0(str, radix, true));
    }

    /**
     * Parse an integer more anally than the built-in parseInt() function,
     * throwing an ArgumentError if there are any invalid characters.
     *
     * The built-in parseInt() will ignore trailing non-integer characters.
     *
     * @param str The string to parse.
     * @param radix The radix to use, from 2 to 16. If not specified the radix will be 10,
     *        unless the String begins with "0x" in which case it will be 16,
     *        or the String begins with "0" in which case it will be 8.
     */
    public static function parseUnsignedInteger (str :String, radix :uint = 0) :uint
    {
        var result :Number = parseInt0(str, radix, false);
        if (result < 0) {
            throw new ArgumentError("parseUnsignedInteger parsed negative value [value=" + str +
                "].");
        }
        return uint(result);
    }

    /**
     * Parse a Number from a String, throwing an ArgumentError if there are any
     * invalid characters.
     *
     * 1.5, 2e-3, -Infinity, Infinity, and NaN are all valid Strings.
     *
     * @param str the String to parse.
     */
    public static function parseNumber (str :String) :Number
    {
        var originalString :String = str;

        if (str == null) {
            throw new ArgumentError("Cannot parseNumber(null)");
        }

        // deal with a few special cases
        if (str == "Infinity") {
            return Infinity;
        } else if (str == "-Infinity") {
            return -Infinity;
        } else if (str == "NaN") {
            return NaN;
        }

        // validate all characters before the "e"
        str = validateDecimal(str, true, true);

        // validate all characters after the "e"
        if (null != str && str.charAt(0) == "e") {
            str = str.substring(1);
            validateDecimal(str, false, false);
        }

        if (null == str) {
            throw new ArgumentError("Could not convert '" + originalString + "' to Number");
        }

        // let Flash do the actual conversion
        return parseFloat(originalString);
    }

    /**
     * Parse a Boolean from a String, throwing an ArgumentError if the String
     * contains invalid characters.
     *
     * "1", "0", and any capitalization variation of "true" and "false" are
     * the only valid input values.
     *
     * @param str the String to parse.
     */
    public static function parseBoolean (str :String) :Boolean
    {
        var originalString :String = str;

        if (str != null) {
            str = str.toLowerCase();
            if (str == "true" || str == "1") {
                return true;
            } else if (str == "false" || str == "0") {
                return false;
            }
        }

        throw new ArgumentError("Could not convert '" + originalString + "' to Boolean");
    }

    /**
     * Append 0 or more copies of the padChar String to the input String
     * until it is at least the specified length.
     */
    public static function pad (
        str :String, length :int, padChar :String = " ") :String
    {
        while (str.length < length) {
            str += padChar;
        }
        return str;
    }

    /**
     * Prepend 0 or more copies of the padChar String to the input String
     * until it is at least the specified length.
     */
    public static function prepad (
        str :String, length :int, padChar :String = " ") :String
    {
        while (str.length < length) {
            str = padChar + str;
        }
        return str;
    }

    /**
     * Substitute "{n}" tokens for the corresponding passed-in arguments.
     */
    public static function substitute (str :String, ... args) :String
    {
        // if someone passed an array as arg 1, fix it
        args = Util.unfuckVarargs(args);
        var len :int = args.length;
        // TODO: FIXME: this might be wrong, if your {0} replacement has a {1} in it, then
        // that'll get replaced next iteration.
        for (var ii : int = 0; ii < len; ii++) {
            str = str.replace(new RegExp("\\{" + ii + "\\}", "g"), args[ii]);
        }
        return str;
    }

    /**
     * Utility function that strips whitespace from the ends of a String.
     */
    public static function trim (str :String) :String
    {
        var startIdx :int = 0;
        var endIdx :int = str.length - 1;
        while (isWhitespace(str.charAt(startIdx))) {
            startIdx++;
        }
        while (endIdx > startIdx && isWhitespace(str.charAt(endIdx))) {
            endIdx--;
        }
        if (endIdx >= startIdx) {
            return str.slice(startIdx, endIdx + 1);
        } else {
            return "";
        }
    }

    /**
     * @return true if the specified String is == to a single whitespace character.
     */
    public static function isWhitespace (character :String) :Boolean
    {
        switch (character) {
        case " ":
        case "\t":
        case "\r":
        case "\n":
        case "\f":
            return true;

        default:
            return false;
        }
    }

    /**
     * Nicely format the specified object into a String.
     */
    public static function toString (obj :*, refs :Dictionary = null) :String
    {
        if (obj == null) { // checks null or undefined
            return String(obj);
        }

        if (refs == null) {
            refs = new Dictionary();

        } else if (refs[obj] !== undefined) {
            return "[cyclic reference]";
        }
        refs[obj] = true;

        var s :String;
        if (obj is Array) {
            var arr :Array = (obj as Array);
            s = "";
            for (var ii :int = 0; ii < arr.length; ii++) {
                if (ii > 0) {
                    s += ", ";
                }
                s += (ii + ": " + toString(arr[ii], refs));
            }
            return "Array(" + s + ")";

        } else if (Util.isPlainObject(obj)) {
            // TODO: maybe do this for any dynamic object? (would have to use describeType)
            s = "";
            for (var prop :String in obj) {
                if (s.length > 0) {
                    s += ", ";
                }
                s += prop + "=>" + toString(obj[prop], refs);
            }
            return "Object(" + s + ")";

        } else if (obj is XML) {
            return Util.XMLtoXMLString(obj as XML);
        }

        return String(obj);
    }

    /**
     * Return a string containing all the public fields of the object
     */
    public static function fieldsToString (buf :StringBuilder, obj :Object) :void
    {
        var appended :Boolean = false;
        for each (var varName :String in Environment.enumerateFields(obj)) {
            if (appended) {
                buf.append(", ");
            }
            buf.append(varName, "=", obj[varName]);
            appended = true;
        }
    }

    /**
     * Return a pretty basic toString of the supplied Object.
     */
    public static function simpleToString (obj :Object) :String
    {
        var buf :StringBuilder = new StringBuilder("[");
        buf.append(ClassUtil.tinyClassName(obj));
        buf.append("(");
        fieldsToString(buf, obj);
        return buf.append(")]").toString();
    }

    /**
     * Truncate the specified String if it is longer than maxLength.
     * The string will be truncated at a position such that it is
     * maxLength chars long after the addition of the 'append' String.
     *
     * @param append a String to add to the truncated String only after
     * truncation.
     */
    public static function truncate (
        s :String, maxLength :int, append :String = "") :String
    {
        if ((s == null) || (s.length <= maxLength)) {
            return s;
        } else {
            return s.substring(0, maxLength - append.length) + append;
        }
    }

    /**
     * Locate URLs in a string, return an array in which even elements
     * are plain text, odd elements are urls (as Strings). Any even element
     * may be an empty string.
     */
    public static function parseURLs (s :String) :Array
    {
        var array :Array = [];
        while (true) {
            var result :Object = URL_REGEXP.exec(s);
            if (result == null) {
                break;
            }

            var index :int = int(result.index);
            var url :String = String(result[0]);
            array.push(s.substring(0, index), url);
            s = s.substring(index + url.length);
        }

        if (s != "" || array.length == 0) { // avoid putting an empty string on the end
            array.push(s);
        }
        return array;
    }

    /**
     * Turn the specified byte array, containing only ascii characters, into a String.
     */
    public static function fromBytes (bytes :ByteArray) :String
    {
        var s :String = "";
        if (bytes != null) {
            for (var ii :int = 0; ii < bytes.length; ii++) {
                s += String.fromCharCode(bytes[ii]);
            }
        }
        return s;
    }

    /**
     * Turn the specified String, containing only ascii characters, into a ByteArray.
     */
    public static function toBytes (s :String) :ByteArray
    {
        if (s == null) {
            return null;
        }
        var ba :ByteArray = new ByteArray();
        for (var ii :int = 0; ii < s.length; ii++) {
            ba[ii] = int(s.charCodeAt(ii)) & 0xFF;
        }
        return ba;
    }

    /**
     * Generates a string from the supplied bytes that is the hex encoded
     * representation of those byts. Returns the empty String for a
     * <code>null</code> or empty byte array.
     */
    public static function hexlate (bytes :ByteArray) :String
    {
        var str :String = "";
        if (bytes != null) {
            for (var ii :int = 0; ii < bytes.length; ii++) {
                var b :int = bytes[ii];
                str += HEX[b >> 4] + HEX[b & 0xF];
            }
        }
        return str;
    }

    /**
     * Turn a hexlated String back into a ByteArray.
     */
    public static function unhexlate (hex :String) :ByteArray
    {
        if (hex == null || (hex.length % 2 != 0)) {
            return null;
        }

        hex = hex.toLowerCase();
        var data :ByteArray = new ByteArray();
        for (var ii :int = 0; ii < hex.length; ii += 2) {
            var value :int = HEX.indexOf(hex.charAt(ii)) << 4;
            value += HEX.indexOf(hex.charAt(ii + 1));

            // TODO: verify
            // values over 127 are wrapped around, restoring negative bytes
            data[ii / 2] = value;
        }

        return data;
    }

    /**
     * Return a hexadecimal representation of an unsigned int, potentially left-padded with
     * zeroes to arrive at of precisely the requested width, e.g.
     *       toHex(131, 4) -> "0083"
     */
    public static function toHex (n :uint, width :uint) :String
    {
        var result :String = "";
        while (width > 0) {
            result = HEX[n & 0x0f] + result;
            n >>>= 4;
            width --;
        }
        return result;
    }

    /**
     * Create line-by-line hexadecimal output with a counter, much like the
     * 'hexdump' Unix utility. For debugging purposes.
     */
    public static function hexdump (bytes :ByteArray) :String
    {
        var str :String = "";
        for (var lineIx :int = 0; lineIx < bytes.length; lineIx += 16) {
            str += toHex(lineIx, 4);
            for (var byteIx :int = 0; byteIx < 16 && lineIx + byteIx < bytes.length; byteIx ++) {
                var b :uint = bytes[lineIx + byteIx];
                str += " " + HEX[b >> 4] + HEX[b & 0x0f];
            }
            str += "\n";
        }
        return str;
        
    }


    /**
     * Internal helper function for parseNumber.
     */
    protected static function validateDecimal (str :String, allowDot :Boolean, allowTrailingE :Boolean) :String
    {
        // skip the leading minus
        if (str.charAt(0) == "-") {
            str = str.substring(1);
        }

        // validate that the characters in the string are all integers,
        // with at most one '.'
        var seenDot :Boolean;
        var seenDigit :Boolean;
        while (str.length > 0) {
            var char :String = str.charAt(0);
            if (char == ".") {
                if (!allowDot || seenDot) {
                    return null;
                }
                seenDot = true;
            } else if (char == "e") {
                if (!allowTrailingE) {
                    return null;
                }
                break;
            } else if (DECIMAL.indexOf(char) >= 0) {
                seenDigit = true;
            } else {
                return null;
            }

            str = str.substring(1);
        }

        // ensure we've seen at least one digit so far
        if (!seenDigit) {
            return null;
        }

        return str;
    }

    /**
     * Internal helper function for parseInteger and parseUnsignedInteger.
     */
    protected static function parseInt0 (str :String, radix :uint, allowNegative :Boolean) :Number
    {
        if (str == null) {
            throw new ArgumentError("Cannot parseInt(null)");
        }

        var negative :Boolean = (str.charAt(0) == "-");
        if (negative) {
            str = str.substring(1);
        }

        // handle this special case immediately, to prevent confusion about
        // a leading 0 meaning "parse as octal"
        if (str == "0") {
            return 0;
        }

        if (radix == 0) {
            if (startsWith(str, "0x")) {
                str = str.substring(2);
                radix = 16;

            } else if (startsWith(str, "0")) {
                str = str.substring(1);
                radix = 8;

            } else {
                radix = 10;
            }

        } else if (radix == 16 && startsWith(str, "0x")) {
            str = str.substring(2);

        } else if (radix < 2 || radix > 16) {
            throw new ArgumentError("Radix out of range: " + radix);
        }

        // now verify that str only contains valid chars for the radix
        for (var ii :int = 0; ii < str.length; ii++) {
            var dex :int = HEX.indexOf(str.charAt(ii).toLowerCase());
            if (dex == -1 || dex >= radix) {
                throw new ArgumentError("Invalid characters in String parseInt='" + arguments[0] +
                    "', radix=" + radix);
            }
        }

        var result :Number = parseInt(str, radix);
        if (isNaN(result)) {
            // this shouldn't happen..
            throw new ArgumentError("Could not parseInt=" + arguments[0]);
        }
        if (negative) {
            result *= -1;
        }
        return result;
    }

    /** Hexidecimal digits. */
    protected static const HEX :Array = [ "0", "1", "2", "3", "4",
        "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" ];

    /** Decimal digits. */
    protected static const DECIMAL :Array = [ "0", "1", "2", "3", "4",
        "5", "6", "7", "8", "9" ];

    /** A regular expression that finds URLs. */
    protected static const URL_REGEXP :RegExp =
        new RegExp("(http|https|ftp)://\\S+", "i");
}
}
