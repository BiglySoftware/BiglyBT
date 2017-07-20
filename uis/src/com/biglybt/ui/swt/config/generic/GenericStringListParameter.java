/*
 * File    : StringListParameter.java
 * Created : 18-Nov-2003
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

package com.biglybt.ui.swt.config.generic;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;

/**
 * @author Olivier
 *
 */
public class GenericStringListParameter{

	private GenericParameterAdapter		adapter;
	private Control list;
	private final String name;
	private final String default_value;
	private final String[] values;
	private final boolean useCombo;

  /**
   *
   * @param composite
   * @param _name
   * @param labels
   * @param values
   * @param bUseCombo
   */
  public GenericStringListParameter(GenericParameterAdapter adapter, Composite composite, String _name,
			String labels[], String values[], boolean bUseCombo) {
		this(adapter, composite, _name, adapter.getStringListValue(_name),
				labels, values, bUseCombo);
	}

  /**
   *
   * @param composite
   * @param _name
   * @param labels
   * @param values
   */
	public GenericStringListParameter(GenericParameterAdapter adapter, Composite composite, String _name,
			String labels[], String values[]) {
		this(adapter, composite, _name, adapter.getStringListValue(_name),
				labels, values, true);
	}

	/**
	 *
	 * @param composite
	 * @param _name
	 * @param defaultValue
	 * @param labels
	 * @param values
	 */
	public GenericStringListParameter(GenericParameterAdapter adapter, Composite composite, String _name,
			String defaultValue, final String labels[], final String values[]) {
		this(adapter, composite, _name, defaultValue, labels, values, true);
	}

	/**
	 *
	 * @param composite
	 * @param _name
	 * @param defaultValue
	 * @param labels
	 * @param values
	 * @param bUseCombo
	 */
	public GenericStringListParameter(GenericParameterAdapter _adapter, Composite composite, String _name,
			String defaultValue, final String labels[], final String values[],
			final boolean bUseCombo) {
		adapter = _adapter;
    this.name = _name;
    this.default_value = defaultValue;
		this.values = values;
		useCombo = bUseCombo;

    if(labels.length != values.length) {
      return;
    }

    String value = adapter.getStringListValue( name, defaultValue );
    int index = findIndex(value,values);
    if (bUseCombo) {
    	list = new Combo(composite,SWT.SINGLE | SWT.READ_ONLY);
    } else {
    	list = new List(composite, SWT.SINGLE | SWT.BORDER | SWT.HORIZONTAL | SWT.VERTICAL) {
    		// I know what I'm doing. Maybe ;)
    		@Override
		    public void checkSubclass() {
    		}

      	// @see org.eclipse.swt.widgets.Text#computeSize(int, int, boolean)
      	@Override
	      public Point computeSize(int wHint, int hHint, boolean changed) {
      		// List widget, at least on Windows, forces the preferred height

      		if ( hHint == 0 && !isVisible()){
    			return( new Point( 0, 0 ));
    		}

      		Point pt = super.computeSize(wHint, hHint, changed);

      		if (hHint == SWT.DEFAULT) {
        		Object ld = getLayoutData();
        		if (ld instanceof GridData) {
        			if (((GridData)ld).grabExcessVerticalSpace) {
        				pt.y = 20;
        			}
        		}
      		}

      		return pt;
      	}
    	};
    }

    for(int i = 0 ; i < labels.length  ;i++) {
    	if (bUseCombo)
    		((Combo)list).add(labels[i]);
    	else
    		((List)list).add(labels[i]);
    }

    setIndex(index);

    list.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event e) {
      	int index;
      	if (bUseCombo)
      		index = ((Combo)list).getSelectionIndex();
      	else
      		index = ((List)list).getSelectionIndex();
      	setIndex(index);

      	/*
        if( change_listeners != null ) {
          for (int i=0;i<change_listeners.size();i++){
            ((ParameterChangeListener)change_listeners.get(i)).parameterChanged(GenericStringListParameter.this,false);
          }
        }
        */
      }
    });
  }

  private int findIndex(String value,String values[]) {
    for(int i = 0 ; i < values.length ;i++) {
      if(values[i].equals( value))
        return i;
    }
    return -1;
  }

	protected void setIndex(final int index) {
		if (index < 0) {
			adapter.setStringListValue(name, null);

			String defValue = adapter.getStringListValue(name);
			int i = findIndex(defValue, values);
			if (i >= 0) {
				// no recursion, because this area only gets called again if i was < 0
				setIndex(i);
			} else {
				Utils.execSWTThread(new AERunnable() {
					@Override
					public void runSupport() {
						if (list == null || list.isDisposed()) {
							return;
						}

				  	if (useCombo) {
			  			((Combo)list).deselectAll();
				  	} else {
			  			((List)list).deselectAll();
				  	}
					}
				});
			}
			return;
		}

  	String selected_value = values[index];

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (list == null || list.isDisposed()) {
					return;
				}

		  	if (useCombo) {
		  		if (((Combo)list).getSelectionIndex() != index) {
		  			((Combo)list).select(index);
		  		}
		  	} else {
		  		if (((List)list).getSelectionIndex() != index) {
		  			((List)list).select(index);
		  		}
		  	}
			}
		});

  	if (!adapter.getStringListValue(name).equals(selected_value)) {
  		adapter.setStringListValue(name, selected_value);
  	}
	}

  public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
    list.setLayoutData(layoutData);
   }

  public Control getControl() {
    return list;
  }

  public String getValue() {
    return adapter.getStringListValue( name, default_value );
  }

  public void setValue(Object value) {
  	if (value instanceof String) {
  		String s = (String)value;
      setIndex(findIndex(s, values));
  	}
  }

  public Object getValueObject() {
  	return getValue();
  }
}