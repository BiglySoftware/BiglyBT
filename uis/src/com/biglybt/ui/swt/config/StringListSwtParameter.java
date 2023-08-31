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

package com.biglybt.ui.swt.config;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.pifimpl.local.ui.config.StringListParameterImpl;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;

import com.biglybt.pif.ui.config.StringListParameter;

/**
 * SWT Parameter for selecting from a list of String values
 * Displayed as Combo box or list box.
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 */
public class StringListSwtParameter
	extends BaseSwtParameter<StringListSwtParameter, String>
{
	public interface ValueProcessor
			extends SwtParameterValueProcessor<StringListSwtParameter, String>
	{
		public default String
		getValue(
			java.util.List<String>	values )
		{
			if ( values.isEmpty()){
				return( null );
			}else{
				String result = values.get(0);
				for ( String v: values.subList( 1, values.size())){
					if (!v.equals(result)){
						return( null );
					}
				}
				return( result );
			}
		}
	}

	private Label lblSuffix;

	private final Control list;

	private final String[] values;

	private final boolean useCombo;

	public StringListSwtParameter(Composite parent,
			StringListParameterImpl param) {
		this(parent, param.getConfigKeyName(), param.getLabelKey(), param.getSuffixLabelKey(),
				param.getValues(), param.getLabels(),
				param.getListType() == StringListParameter.TYPE_DROPDOWN, null);
		setPluginParameter(param);
	}

	/**
	 * Make UI components for a list of String values
	 * <p/>
	 * When parent composite is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Composite is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the text shown before text box
	 * @param suffixLabelKey Messagebundle key for text shown after the text box
	 * @param values list of values that can be stored
	 * @param displayStrings fancy words representing each value 
	 * @param bUseCombo true - Combo; false - list box 
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public StringListSwtParameter(Composite composite, String paramID,
			String labelKey, String suffixLabelKey, String[] values,
			String[] displayStrings, boolean bUseCombo,
			SwtParameterValueProcessor<StringListSwtParameter, String> valueProcessor) {
		super(paramID);
		this.values = values;
		useCombo = bUseCombo;

		boolean doGridData = doGridData(composite);

		Control label = createStandardLabel(composite, labelKey);
		if (label != null && doGridData) {
			GridData gridData = new GridData();
			if (!bUseCombo) {
				gridData.horizontalSpan = 2;
			}
			label.setLayoutData(gridData);
		}

		Composite parent;
		if (suffixLabelKey == null) {
			parent = composite;
		} else {
			parent = new Composite(composite, SWT.NONE);
			GridLayout gridLayout = new GridLayout(2, false);
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			parent.setLayout(gridLayout);
			if (doGridData(composite)) {
				parent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			}
		}

		if (displayStrings.length != values.length) {
			throw new ArrayIndexOutOfBoundsException();
		}

		if (bUseCombo) {
			list = new Combo(parent, SWT.SINGLE | SWT.READ_ONLY);
		} else {
			list = new List(parent,
					SWT.SINGLE | SWT.BORDER | SWT.HORIZONTAL | SWT.VERTICAL) {
				// I know what I'm doing. Maybe ;)
				@Override
				public void checkSubclass() {
				}

				// @see org.eclipse.swt.widgets.Text#computeSize(int, int, boolean)
				@Override
				public Point computeSize(int wHint, int hHint, boolean changed) {
					// List widget, at least on Windows, forces the preferred height

					if (hHint == 0 && !isVisible()) {
						return (new Point(0, 0));
					}

					Point pt = super.computeSize(wHint, hHint, changed);

					if (hHint == SWT.DEFAULT) {
						Object ld = getLayoutData();
						if (ld instanceof GridData) {
							if (((GridData) ld).grabExcessVerticalSpace) {
								pt.y = 20;
							}
						}
					}

					return pt;
				}
			};
		}
		setMainControl(list);

		if (doGridData) {
			GridData gridData = new GridData(
					bUseCombo ? 0 : GridData.FILL_HORIZONTAL);
			if (!bUseCombo) {
				gridData.horizontalSpan = 2;
			}
			list.setLayoutData(gridData);
		}

		for (String displayString : displayStrings) {
			if (Utils.isGTK) {
				displayString += " ";
			}
			if (bUseCombo) {
				((Combo) list).add(displayString);
			} else {
				((List) list).add(displayString);
			}
		}

		if (suffixLabelKey != null) {
			lblSuffix = new Label(parent, SWT.WRAP);
			Messages.setLanguageText(lblSuffix, suffixLabelKey);
			lblSuffix.setLayoutData(Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
			ClipboardCopy.addCopyToClipMenu( lblSuffix );
		}


		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (paramID != null) {
			setConfigValueProcessor(String.class);
		} else {
			refreshControl();
		}

		list.addListener(SWT.Selection, e -> {
			int index = bUseCombo ? ((Combo) list).getSelectionIndex()
					: ((List) list).getSelectionIndex();
			setValue(values[index]);
		});
	}

	private static int findIndex(String value, String[] values) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(value))
				return i;
		}
		return -1;
	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		Utils.execSWTThread(() -> {
			if (list.isDisposed()) {
				return;
			}
			refreshSuffixControl(lblSuffix);

			int index = findIndex(getValue(), values);
			if (index < 0) {
				// XXX Does deselectAll trigger SWT.Selection?
				if (useCombo) {
					if (((Combo) list).getSelectionIndex() != -1) {
						((Combo) list).deselectAll();
					}
				} else {
					if (((List) list).getSelectionIndex() != -1) {
						((List) list).deselectAll();
					}
				}

				resetToDefault();
			} else {
				if (useCombo) {
					if (((Combo) list).getSelectionIndex() != index) {
						((Combo) list).select(index);
					}
				} else {
					if (((List) list).getSelectionIndex() != index) {
						((List) list).select(index);
					}
				}
			}
		});
	}

	@Override
	public Control[] getControls() {
		if (lblSuffix == null) {
			return super.getControls();
		}
		java.util.List<Control> list =  new ArrayList<>(Arrays.asList(super.getControls()));
		list.add(lblSuffix);
		return list.toArray(new Control[0]);
	}

}
