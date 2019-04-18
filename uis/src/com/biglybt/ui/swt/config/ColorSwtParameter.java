/*
 * Created : 12 nov. 2003
 * By      : Olivier
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
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.ConfigurationParameterNotFoundException;
import com.biglybt.core.html.HTMLUtils;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.SystemTime;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.widgets.ButtonWithMinWidth;

import com.biglybt.pif.ui.config.ColorParameter;
import com.biglybt.pif.ui.config.Parameter;

/**
 * SWT Parameter representing a color (r, g, b) value. 
 * Button with color opting a color choosing dialog.
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 */
public class ColorSwtParameter
	extends BaseSwtParameter<ColorSwtParameter, int[]>
{
	private final Button colorChooser;

	private Button resetButton;

	private Label lblSuffix;

	private Image img;

	public ColorSwtParameter(Composite composite, ColorParameter param) {
		this(composite, param.getConfigKeyName(), param.getLabelKey(),
				param.getSuffixLabelKey(), true, null);
		setPluginParameter(param);
	}

	/**
	 * Make a color selecting button.
	 * <p/>
	 * When parent is of GridLayout, resulting new widgets will take 2 columns
	 *
	 * @param composite Where widgets will be placed. Composite is not altered
	 * @param paramID ID of the parameter (usually config id)
	 * @param labelKey Messagebundle key for the text shown before the color button
	 * @param suffixLabelKey Messagebundle key for text shown after the color button (same row)
	 * @param showResetButton Whether to display a rest button next to the color button 
	 * @param valueProcessor null if you want to use COConfigurationManager
	 */
	public ColorSwtParameter(Composite composite, //
			String paramID, String labelKey, String suffixLabelKey, //
			boolean showResetButton,
			SwtParameterValueProcessor<ColorSwtParameter, int[]> valueProcessor) {
		super(paramID);

		createStandardLabel(composite, labelKey);

		Composite parent;
		if (suffixLabelKey == null && !showResetButton) {
			parent = composite;
		} else {
			parent = new Composite(composite, SWT.NONE);
			GridLayout gridLayout = new GridLayout(showResetButton ? 3 : 2, false);
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			parent.setLayout(gridLayout);
			if (doGridData(composite)) {
				parent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			}
		}

		colorChooser = new ButtonWithMinWidth(parent, SWT.PUSH, 40);
		setMainControl(colorChooser);

		if (doGridData(composite)) {
			GridData gridData = new GridData();
			colorChooser.setLayoutData(gridData);
		}

		if (showResetButton) {
			resetButton = new Button(parent, SWT.PUSH);
			Messages.setLanguageText(resetButton,
					"ConfigView.section.style.colorOverrides.reset");
			resetButton.addListener(SWT.Selection, event -> resetToDefault());
			if (paramID != null) {
				resetButton.setEnabled(
						COConfigurationManager.getBooleanParameter(paramID + ".override"));
			}
		}

		if (suffixLabelKey != null) {
			lblSuffix = new Label(parent, SWT.WRAP);
			Messages.setLanguageText(lblSuffix, suffixLabelKey);
			lblSuffix.setLayoutData(
					Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL));
			ClipboardCopy.addCopyToClipMenu(lblSuffix);
		}

		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (this.paramID != null) {
			setValueProcessor(new ColorValueProcessor(this));
		}

		Menu menu = new Menu(colorChooser);
		colorChooser.setMenu(menu);
		MenuItem mi = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(mi,
				"ConfigView.section.style.colorOverrides.reset");
		mi.addListener(SWT.Selection, (e) -> resetToDefault());

		menu.addMenuListener( MenuListener.menuShownAdapter(
			    	(e)->{ mi.setEnabled( !isDefaultValue());}));
		
		colorChooser.addListener(SWT.Dispose, e -> {
			if (img != null && !img.isDisposed()) {
				img.dispose();
			}
		});

		colorChooser.addListener(SWT.Selection, e -> {
			ColorDialog cd = new ColorDialog(composite.getShell());

			List<RGB> custom_colours = Utils.getCustomColors();

			int[] value = getValue();
			if (value != null && value.length == 3) {

				RGB colour = new RGB(value[0], value[1], value[2]);

				custom_colours.remove(colour);

				custom_colours.add(0, colour);

				cd.setRGB(colour);
			}

			cd.setRGBs(custom_colours.toArray(new RGB[0]));

			RGB newColor = cd.open();

			if (newColor == null) {

				return;
			}

			Utils.updateCustomColors(cd.getRGBs());

			int[] newColorInts = {
				newColor.red,
				newColor.green,
				newColor.blue
			};

			setValue(newColorInts);
		});

	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		if (DEBUG) {
			int[] value = getValue();
			debug("refreshControl " + (value == null ? value
					: HTMLUtils.toColorHexString(value[0], value[1], value[2], 255)));
		}
		Utils.execSWTThread(() -> {
			if (colorChooser.isDisposed()) {
				return;
			}
			int[] value = getValue();
			updateButtonColor(colorChooser.getDisplay(), value);

			refreshSuffixControl(lblSuffix);

			if (valueProcessor != null) {
				if (resetButton != null) {
					resetButton.setEnabled(!valueProcessor.isDefaultValue(this));
				}
			}
		});
	}

	private void updateButtonColor(final Display display, int[] rgb) {
		boolean needRelayout;
		Image oldImg = img;
		if (rgb != null && rgb.length > 2 && rgb[0] >= 0 && rgb[1] >= 0
				&& rgb[2] >= 0) {
			needRelayout = oldImg == null;
			boolean showHexCode = Utils.getUserMode() > Parameter.MODE_BEGINNER;

			int h = FontUtils.getFontHeightInPX(colorChooser.getFont());
			int w = showHexCode ? h : 25;
			img = new Image(display, w, h);
			GC gc = new GC(img);
			try {
				Color color = ColorCache.getColor(display, rgb[0], rgb[1], rgb[2]);
				if (color != null) {
					gc.setBackground(color);
					gc.fillRectangle(0, 0, w, h);
				}
			} finally {
				gc.dispose();
			}
			colorChooser.setImage(img);
			String hexCode = "#" + HTMLUtils.toColorHexString(rgb[0], rgb[1], rgb[2], 255);
			if (showHexCode) {
				colorChooser.setText(hexCode);
			} else {
				colorChooser.setToolTipText(hexCode);
			}
		} else {
			needRelayout = oldImg != null;
			img = null;
			colorChooser.setImage(img);
			colorChooser.setText(MessageText.getString("Button.set"));
		}
		if (oldImg != null && !oldImg.isDisposed()) {
			oldImg.dispose();
		}
		if (needRelayout) {
			// Note on Windows: No amount of invalidation will fix the bug
			// of having a gap before the text, after setting the image from non-null
			// to null
			colorChooser.requestLayout();
		}
	}

	@Override
	public Control[] getControls() {
		if (lblSuffix == null) {
			return super.getControls();
		}
		List<Control> list = new ArrayList<>(Arrays.asList(super.getControls()));
		list.add(lblSuffix);
		return list.toArray(new Control[0]);
	}

	public void setColor(int _r, int _g, int _b) {
		setValue(new int[] {
			_r,
			_g,
			_b
		});
	}

	private static class ColorValueProcessor
		implements SwtParameterValueProcessor<ColorSwtParameter, int[]>,
		ParameterListener
	{
		private final SwtParameter owner;

		private boolean changing;

		public ColorValueProcessor(SwtParameter owner) {
			this.owner = owner;
			String configID = owner.getParamID();
			COConfigurationManager.addWeakParameterListener(this, false,
					configID + ".red");
			COConfigurationManager.addWeakParameterListener(this, false,
					configID + ".green");
			COConfigurationManager.addWeakParameterListener(this, false,
					configID + ".blue");
			COConfigurationManager.addWeakParameterListener(this, false,
					configID + ".override");
		}

		@Override
		public void parameterChanged(String parameterName) {
			try {
				if (owner.isDisposed()) {
					COConfigurationManager.removeParameterListener(parameterName, this);
					return;
				}
				if (owner.DEBUG) {
					owner.debug(parameterName + " changed via "
							+ Debug.getCompressedStackTrace());
				}

				if (changing) {
					return;
				}

				SimpleTimer.addEvent("Color.Update", SystemTime.getOffsetTime(300),
						event -> {
							changing = false;
							owner.informChanged();
						});
			} catch (Exception e) {
				Debug.out(
						"parameterChanged trigger from ConfigParamAdapter " + parameterName,
						e);
			}
		}

		@Override
		public int[] getValue(ColorSwtParameter p) {
			String key = p.getParamID();
			return new int[] {
				COConfigurationManager.getIntParameter(key + ".red"),
				COConfigurationManager.getIntParameter(key + ".green"),
				COConfigurationManager.getIntParameter(key + ".blue")
			};
		}

		@Override
		public boolean setValue(ColorSwtParameter p, int[] value) {
			String key = p.getParamID();
			return COConfigurationManager.setRGBParameter(key, value, true);
		}

		@Override
		public boolean isDefaultValue(ColorSwtParameter p) {
			return !COConfigurationManager.getBooleanParameter(
					p.getParamID() + ".override");
		}

		@Override
		public int[] getDefaultValue(ColorSwtParameter p) {
			String key = p.getParamID();
			ConfigurationDefaults defaults = ConfigurationDefaults.getInstance();
			try {
				return new int[] {
					defaults.getIntParameter(key + ".red"),
					defaults.getIntParameter(key + ".green"),
					defaults.getIntParameter(key + ".blue")
				};
			} catch (ConfigurationParameterNotFoundException e) {
				e.printStackTrace();
			}
			return new int[] {
				80,
				80,
				80
			};
		}

		@Override
		public boolean resetToDefault(ColorSwtParameter p) {
			return COConfigurationManager.removeRGBParameter(p.getParamID());
		}

	}

}
