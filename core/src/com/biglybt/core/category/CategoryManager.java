/*
 * File    : CategoryManager.java
 * Created : 09 feb. 2004
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


package com.biglybt.core.category;

import com.biglybt.core.category.impl.CategoryManagerImpl;

/** A singleton to manage Categories of Torrents (DownloadManagers).
 * @author TuxPaper
 */
public class CategoryManager {
  /** Add a CategoryManager Listener
   * @param l Listener to Add
   */
  public static void addCategoryManagerListener(CategoryManagerListener l) {
    CategoryManagerImpl.getInstance().addCategoryManagerListener(l);
  }

  /** Removes a CategoryManager Listener
   * @param l Listener to remove
   * @see CategoryManagerListener
   */
  public static void removeCategoryManagerListener(CategoryManagerListener l) {
    CategoryManagerImpl.getInstance().removeCategoryManagerListener(l);
  }

  /** Creates a new Category object and adds it to the list
   * @return If successful, returns the newly created Category.  Otherwise, returns null.
   * @param name Name of Category to add
   */
  public static Category createCategory(String name) {
    return CategoryManagerImpl.getInstance().createCategory(name);
  }

  /** Removes a Category from the list
   * @param category Category to remove
   */
  public static void removeCategory(Category category) {
    CategoryManagerImpl.getInstance().removeCategory(category);
  }

  /** Retrieves the list of Categories
   * To sort the categories by name (TYPE_USER last):
   * <CODE>
   * Arrays.sort(categories);
   * </CODE>
   *
   * @return List of Categories
   */
  public static Category[] getCategories() {
    return CategoryManagerImpl.getInstance().getCategories();
  }

  /** Retrieve a Category
   * @param name Name of Category
   * @return Category you asked for
   */
  public static Category getCategory(String name) {
    return CategoryManagerImpl.getInstance().getCategory(name);
  }

  /** Retrieve a non-user Category
   * @param type any type except TYPE_USER
   * @return The Category, or null if not found.
   * @see Category
   * USER_ constants
   */
  public static Category getCategory(int type) {
    return CategoryManagerImpl.getInstance().getCategory(type);
  }
  
  public static int getCategorisedDownloadCount(){
	  return CategoryManagerImpl.getInstance().getCategorisedDownloadCount();
  }
}
