/* Written and copyright 2001-2003 Tobias Minich.
 *
 * LegacyHashtable.java
 *
 * Created on 31. August 2003, 18:16
 * Copyright (C) 2003, 2004, 2005, 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */

package com.biglybt.ui.common.util;

import java.util.Hashtable;

/**
 *
 * @author  tobi
 */
public class LegacyHashtable extends Hashtable {
  /** Creates a new instance of LegacyHashtable */
  public LegacyHashtable() {
    super();
  }

  @Override
  public Object get(Object key) {
    if (containsKey(key))
      return super.get(key);
    else
      return "";
  }

}
