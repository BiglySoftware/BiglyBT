/*
 * File    : RPException.java
 * Created : 28-Jan-2004
 * By      : parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.remote;

import com.biglybt.core.util.Debug;

/**
 * @author parg
 *
 */
public class RPException extends RuntimeException {

    private static void checkErrorType(Throwable e) {
        if (e instanceof RPException) {
            Debug.outNoStack("RPExceptions chained together - stack trace, followed by other RPException stack trace.");
            Debug.outStackTrace();
            Debug.printStackTrace(e);
            throw new RuntimeException("cannot chain RPException instances together");
        }
    }

    public RPException(String str) {
        super(str);
    }

    public RPException(String str, Throwable e) {
        super(str, e);
        checkErrorType(e);
    }

    public RPException(Throwable e) {
        super(e);
        checkErrorType(e);
    }

    public String getRPType() {
        return null;
    }

    public Throwable getSerialisableObject() {
        Throwable t = this.getCause();
        if (t == null) {
            return this;
        }
        else {
            return t;
        }
    }



    public Class getErrorClass() {
        Throwable t = this.getCause();
        if (t == null) {return null;}
        return t.getClass();
    }

}
