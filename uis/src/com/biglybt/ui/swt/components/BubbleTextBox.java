/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.components;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.utils.FontUtils;

/**
 * TextBox with a "search bubble" style around it.  Search icon on left, X on the right
 *
 * @author TuxPaper
 */
public class BubbleTextBox
	implements PaintListener
{
	public interface BubbleTextBoxChangeListener
	{
		void bubbleTextBoxChanged(BubbleTextBox bubbleTextBox);
	}

	public static final String REGEX_BUTTON_TEXT = ".\u2731";

	private static final int REGEX_BUTTON_PADDING = 4;

	private static final int BUTTON_NONE = 0;

	private static final int BUTTON_REGEX = 2;

	private static final int BUTTON_CLEAR = 1;

	private static Font FONT_REGEX_BUTTON;

	private static Color COLOR_FILTER_REGEX;

	private static Color COLOR_FILTER_NO_REGEX;

	private Font FONT_NO_REGEX;

	private Font FONT_REGEX;

	private Font FONT_REGEX_ERROR;

	private final Text textWidget;

	private final Composite cBubble;

	private static final int TEXTBOX_VPADDING = (Utils.isGTK3) ? 1 : 3;

	private final int INDENT_OVAL;

	private final int WIDTH_CLEAR;

	private final int WIDTH_PADDING;

	private String text = "";

	private boolean allowRegex;

	private boolean regexEnabled = false;

	private boolean regexIsError = false;

	private int mouseOverButton = BUTTON_NONE;

	private String regexError = null;

	private String tooltip = null;

	List<BubbleTextBoxChangeListener> bubbleTextBoxChangeListeners = new ArrayList<>();

	static {
		COConfigurationManager.addWeakParameterListener((n) -> {

			COLOR_FILTER_NO_REGEX = Utils.getConfigColor("table.filter.active.colour",
					Colors.fadedBlue);
			COLOR_FILTER_REGEX = Utils.getConfigColor("table.filter.regex.colour",
					Colors.fadedYellow);

		}, true, "table.filter.active.colour", "table.filter.regex.colour");
	}

	private KeyListener keyListener;

	public BubbleTextBox(Composite parent, int style) {
		cBubble = new Composite(parent, SWT.DOUBLE_BUFFERED);
		FormLayout layout = new FormLayout();
		cBubble.setLayout(layout);

		int textStyle;
		if ( Constants.isOSX && Utils.isDarkAppearanceNative()){
			
				// if we don't force a border on OSX dark mode then it messes up
			
			textStyle = ( style | SWT.BORDER ) & ~(SWT.SEARCH);
		}else{
			textStyle = style & ~(SWT.BORDER | SWT.SEARCH);
		}
		textWidget = new Text(cBubble, textStyle ) {
			@Override
			protected void checkSubclass() {
			}

			@Override
			public Point computeSize(int wHint, int hHint, boolean changed) {
				Point point = super.computeSize(wHint, hHint, changed);
				if (Utils.isGTK3|| (Constants.isOSX && Utils.isDarkAppearanceNative())){
					Rectangle area = getParent().getClientArea();
					//System.out.println("computedSize = " + point  + "; parent.h=" + area.height);

					// Bug in SWT: Seems Text widget on GTK3 doesn't obey parent's fixed height
					int diff = TEXTBOX_VPADDING * 2;
					if (area.height > diff) {
						//if (point.y != area.height - diff) System.out.println("Set textbox height=" + point.y);
						point.y = area.height - diff;
					}
				}
				return point;
			}
		};
		cBubble.addListener(SWT.Resize, e -> setupTextWidgetLayoutData());

		// Temporary hack until we remove TableViewPainted.enableFilterCheck(Text txtFilter, TableViewFilterCheck<Object> filterCheck)
		textWidget.setData("BubbleTextBox", this);

		Runnable runOnFontSizeChange = () -> {
			boolean existingFont = FONT_REGEX_BUTTON != null;
			if (existingFont) {
				FontUtils.uncache( FONT_REGEX_BUTTON );
			}
			FONT_REGEX_BUTTON = FontUtils.cache( FontUtils.getFontWithStyle(textWidget.getFont(),
					SWT.NORMAL, 1.0f));
			if (existingFont) {
				cBubble.redraw();
			}
		};
		runOnFontSizeChange.run();
		FontUtils.fontToWidgetHeight(textWidget, runOnFontSizeChange);
		textWidget.addDisposeListener(e -> {
			if (FONT_REGEX_BUTTON != null) {
				FontUtils.uncache( FONT_REGEX_BUTTON );
				FONT_REGEX_BUTTON = null;
			}
		});

		if (Utils.isGTK3) {
			Display display = textWidget.getDisplay();
			textWidget.setBackground(
					Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND));
			textWidget.setForeground(
					Colors.getSystemColor(display, SWT.COLOR_LIST_FOREGROUND));
		}

		INDENT_OVAL = 6;
		WIDTH_CLEAR = 7;
		WIDTH_PADDING = 6;

		setupTextWidgetLayoutData();

		cBubble.addPaintListener(this);

		cBubble.addListener(SWT.MouseDown, event -> {
			switch (mouseOverButton) {
				case BUTTON_CLEAR:
					textWidget.setText("");
					break;
				case BUTTON_REGEX:
					setRegexEnabled(!regexEnabled);
					break;
			}
		});

		cBubble.addListener(SWT.MouseExit, event -> {
			if (mouseOverButton != BUTTON_NONE) {
				mouseOverButton = BUTTON_NONE;
				cBubble.redraw();
			}
		});

		cBubble.addListener(SWT.MouseMove, event -> {
			int mouseNowOverButton = BUTTON_NONE;
			Rectangle r = (Rectangle) event.widget.getData("XArea");
			if (r != null && r.contains(event.x, event.y) && !text.isEmpty()) {
				mouseNowOverButton = BUTTON_CLEAR;
			} else {
				r = (Rectangle) event.widget.getData("RegexArea");
				if (r != null && r.contains(event.x, event.y)) {
					mouseNowOverButton = BUTTON_REGEX;
				}
			}
			if (mouseOverButton != mouseNowOverButton) {
				mouseOverButton = mouseNowOverButton;
				cBubble.redraw();
			}
		});

		Listener listenerMouseHover = event -> {
			String tt = null;
			switch (mouseOverButton) {
				case BUTTON_NONE:
					tt = regexIsError ? regexError : text.isEmpty() ? tooltip : null;
					break;
				case BUTTON_REGEX:
					tt = regexIsError ? regexError
							: MessageText.getString("label.regexps");
					break;
				case BUTTON_CLEAR:
					tt = MessageText.getString("MyTorrentsView.clearFilter.tooltip");
					break;
			}
			cBubble.setToolTipText(tt);
			textWidget.setToolTipText(tt);
		};
		cBubble.addListener(SWT.MouseHover, listenerMouseHover);
		textWidget.addListener(SWT.MouseHover, listenerMouseHover);

		// pick up changes in the text control's bg color and propagate to the bubble

		textWidget.addPaintListener(new PaintListener() {
			private Color existing_bg;

			@Override
			public void paintControl(PaintEvent arg0) {
				Color current_bg = textWidget.getBackground();

				if (!current_bg.equals(existing_bg)) {

					existing_bg = current_bg;

					cBubble.redraw();
				}
			}
		});

		textWidget.addModifyListener(e -> {
			boolean textWasBlank = text.length() == 0;
			text = textWidget.getText();
			boolean textIsBlank = text.length() == 0;
			validateFilterRegex();
			if (textWasBlank != textIsBlank) {
				setupTextWidgetLayoutData();
				cBubble.redraw();
			}
			refilter();
		});

		textWidget.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (!e.doit) {
					return;
				}
				if (allowRegex) {
					int key = e.character;
					if (key <= 26 && key > 0) {
						key += 'a' - 1;
					}

					if (e.stateMask == SWT.MOD1) {
						switch (key) {
							case 'x': { // CTRL+X: RegEx search switch
								setRegexEnabled(!regexEnabled);
								e.doit = false; // prevent sound from this key
								return;
							}
						}
					}
				}

				if (keyListener != null && !ignoreListener( e )) {
					keyListener.keyPressed(e);
					if (!e.doit) {
						return;
					}
				}
				super.keyPressed(e);
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (!e.doit) {
					return;
				}

				if (keyListener != null && !ignoreListener( e )) {
					keyListener.keyReleased(e);
					if (!e.doit) {
						return;
					}
				}
				super.keyReleased(e);
			}
			
			private boolean
			ignoreListener(
				KeyEvent e )
			{
				if ( e.stateMask == SWT.MOD1 ){
					int keyCode = e.keyCode;
						// we want copy/paste to be handled by the text box
					if ( keyCode == 'c' || keyCode == 'v' ){
						return(true);
					}
				}
				return( false );
			}
		});
	}

	private int
	getBubbleLayoutHeight()
	{
			// Note we have the equivalent skin settings for this (in two places as of now)
			// 		filterbox.height=1.4rem
			//		filterbox.height._mac._dark=2.0rem
		
		float fontHeight = FontUtils.getFontHeightInPX(textWidget.getFont());
		
		if ( Constants.isOSX && Utils.isDarkAppearanceNative()){
			
			return((int)( fontHeight * 2.0 ));
			
		}else{
			
			return((int)( fontHeight * 1.4 ));
		}
	}
	
	public void
	setMessageAndLayout(
		String		msg,
		GridData	gridData )
	{
		textWidget.setMessage( msg );
		gridData.heightHint = getBubbleLayoutHeight();
		cBubble.setLayoutData(gridData);
	}
	
	public void
	setMessageAndLayout(
		String		msg,
		FormData	formData )
	{
		textWidget.setMessage( msg );
	    formData.height = getBubbleLayoutHeight();
	    cBubble.setLayoutData(formData);
	}	
	
	@Override
	public void paintControl(PaintEvent e) {
		Rectangle clientArea = cBubble.getClientArea();
		//System.out.println("paint " + BubbleTextBox.this + "; " + e + "; " + e.gc.getClipping() + "; " + clientArea);
		e.gc.setBackground(textWidget.getBackground());
		e.gc.setAdvanced(true);
		e.gc.setAntialias(SWT.ON);
		e.gc.fillRoundRectangle(clientArea.x, clientArea.y, clientArea.width - 1,
				clientArea.height - 1, clientArea.height, clientArea.height);
		if ( !Utils.isDarkAppearanceNative()){
			e.gc.setAlpha(127);
			e.gc.drawRoundRectangle(clientArea.x, clientArea.y, clientArea.width - 1,
					clientArea.height - 1, clientArea.height, clientArea.height);
			e.gc.setAlpha(255);
		}
		e.gc.setLineCap(SWT.CAP_FLAT);

		int fontHeight = FontUtils.getFontHeightInPX(textWidget.getFont());
		if (fontHeight > 17 - INDENT_OVAL - 1) {
			fontHeight = 17 - INDENT_OVAL - 1;
		}
		float heightOval = fontHeight * 0.7f;
		float widthOval = heightOval;

		Color colorFadedText;
		
		if ( Utils.isGTK3 && Utils.isDarkAppearanceNative()){
			colorFadedText = Colors.light_grey;		// COLOR_WIDGET_NORMAL_SHADOW is black :(
		}else{
			colorFadedText = Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_NORMAL_SHADOW);
		}
		e.gc.setForeground(colorFadedText);

		int iconY = clientArea.y + ((clientArea.height - fontHeight + 1) / 2);

		e.gc.setLineWidth(2);
		e.gc.drawOval(clientArea.x + INDENT_OVAL, iconY, (int) widthOval,
				(int) heightOval);
		e.gc.drawPolyline(new int[] {
			(int) (clientArea.x + INDENT_OVAL + widthOval - 1),
			(int) (iconY + heightOval - 1),
			clientArea.x + INDENT_OVAL + fontHeight,
			iconY + fontHeight,
		});

		int endPosX = clientArea.x + clientArea.width - WIDTH_PADDING;

		boolean textIsBlank = textWidget.getText().isEmpty();
		if (!textIsBlank) {
			int YADJ = (clientArea.height
					- (WIDTH_CLEAR + WIDTH_PADDING + WIDTH_PADDING)) / 2;
			e.gc.setLineCap(SWT.CAP_ROUND);
			//e.gc.setLineWidth(1);
			endPosX = clientArea.x + clientArea.width
					- (WIDTH_CLEAR + WIDTH_PADDING + (WIDTH_PADDING / 2));
			Rectangle rXArea = new Rectangle(endPosX,
					clientArea.y + (WIDTH_PADDING / 2), WIDTH_CLEAR + WIDTH_PADDING,
					clientArea.height - WIDTH_PADDING);
			cBubble.setData("XArea", rXArea);

			if (mouseOverButton == BUTTON_CLEAR) {
				//e.gc.setBackground(
				//		Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_LIGHT_SHADOW));
				e.gc.fillOval(rXArea.x, rXArea.y, rXArea.width, rXArea.height);

				e.gc.setForeground(textWidget.getForeground());
			}

			e.gc.drawPolyline(new int[] {
				clientArea.x + clientArea.width - WIDTH_PADDING,
				clientArea.y + WIDTH_PADDING + YADJ,
				clientArea.x + clientArea.width - (WIDTH_PADDING + WIDTH_CLEAR),
				clientArea.y + WIDTH_PADDING + WIDTH_CLEAR + YADJ,
			});
			e.gc.drawPolyline(new int[] {
				clientArea.x + clientArea.width - WIDTH_PADDING,
				clientArea.y + WIDTH_PADDING + WIDTH_CLEAR + YADJ,
				clientArea.x + clientArea.width - (WIDTH_PADDING + WIDTH_CLEAR),
				clientArea.y + WIDTH_PADDING + YADJ,
			});
		}

		if (allowRegex) {
			e.gc.setFont(FONT_REGEX_BUTTON);
			Point regexTextSize = e.gc.textExtent(REGEX_BUTTON_TEXT);

			Rectangle regexArea = new Rectangle(
					endPosX - regexTextSize.x - (WIDTH_PADDING / 2)
							- REGEX_BUTTON_PADDING,
					clientArea.y + 1,
					regexTextSize.x + (WIDTH_PADDING / 2) + REGEX_BUTTON_PADDING,
					clientArea.height - 2);
			cBubble.setData("RegexArea", regexArea);

			/*
			if (mouseOverButton == BUTTON_REGEX) {
				e.gc.setBackground(
						Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_LIGHT_SHADOW));
				e.gc.fillRoundRectangle(regexArea.x, regexArea.y, regexArea.width-2,
						regexArea.heigh-1, 5, 5);
			}
			*/
			
			if (regexEnabled) {
				if (mouseOverButton != BUTTON_REGEX) {
					if ( regexIsError ){
						e.gc.setBackground(Colors.fadedRed );
					}
					e.gc.fillRoundRectangle(regexArea.x, clientArea.y,
							regexArea.width - 2, clientArea.height - 1, 5, 5);
					e.gc.setForeground(colorFadedText);
				} else if (regexIsError) {
					e.gc.setForeground(Colors.colorError);
				} else {
					e.gc.setForeground(colorFadedText);
				}

				e.gc.setLineWidth(1);
				e.gc.drawRoundRectangle(regexArea.x, clientArea.y, regexArea.width - 2,
						clientArea.height - 1, 5, 5);
			}

			e.gc.setForeground(regexEnabled || mouseOverButton == BUTTON_REGEX
					? textWidget.getForeground() : colorFadedText);
			int xOfs = regexTextSize.x - (regexArea.width / 2);
			int yPos = regexArea.y + ((regexArea.height - regexTextSize.y) / 2) + 1;
			e.gc.drawText(REGEX_BUTTON_TEXT.substring(0, 1), regexArea.x + xOfs, yPos,
					true);
			e.gc.drawText(REGEX_BUTTON_TEXT.substring(1),
					regexArea.x + e.gc.textExtent(".").x + xOfs, yPos - 2, true);

		}
	}

	private void setupTextWidgetLayoutData() {
		int bubbleHeight = cBubble.getClientArea().height;
		//System.out.println("setupTextWidgetLayoutData; bubbleHeight=" + bubbleHeight);
		FormData fd = new FormData();
		fd.top = new FormAttachment(0, TEXTBOX_VPADDING);
		fd.bottom = new FormAttachment(100, -TEXTBOX_VPADDING);
		if (bubbleHeight > 0) {
			fd.height = Math.max(1, bubbleHeight - (TEXTBOX_VPADDING * 2));
		}
		fd.left = new FormAttachment(0, 17);
		boolean isClearButtonVisible = !textWidget.getText().isEmpty();
		int right;
		if (!allowRegex && !isClearButtonVisible && bubbleHeight > 0) {
			right = -(bubbleHeight / 2);
		} else {
			right = -WIDTH_PADDING;
			if (isClearButtonVisible) {
				right -= (WIDTH_PADDING + WIDTH_CLEAR + (WIDTH_PADDING / 2));
			}
			if (allowRegex) {
				GC gc = new GC(this.textWidget);
				gc.setFont(FONT_REGEX_BUTTON);
				Point regexTextSize = gc.textExtent(REGEX_BUTTON_TEXT);
				gc.dispose();
				right -= (regexTextSize.x + REGEX_BUTTON_PADDING);
				if (isClearButtonVisible) {
					right -= WIDTH_PADDING;
				} else {
					right -= (WIDTH_PADDING / 2);
				}
			}
		}
		fd.right = new FormAttachment(100, right);
		textWidget.setLayoutData(fd);
		cBubble.layout();
	}

	public Composite getMainWidget() {
		return cBubble;
	}

	public Text getTextWidget() {
		return textWidget;
	}

	public boolean isOurWidget(Widget widget) {
		return widget == textWidget || widget == cBubble;
	}

	public boolean isDisposed() {
		return textWidget.isDisposed() || cBubble.isDisposed();
	}

	public void setFocus() {
		textWidget.setFocus();
	}

	public void setAllowRegex(boolean allowRegex) {
		if (this.allowRegex == allowRegex) {
			return;
		}
		this.allowRegex = allowRegex;
		cBubble.redraw();
		setupTextWidgetLayoutData();
		validateFilterRegex();
		refilter();
	}

	public boolean allowRegex() {
		return allowRegex;
	}

	private void setRegexEnabled(boolean enabled) {
		if (regexEnabled == enabled) {
			return;
		}
		regexEnabled = enabled;
		cBubble.redraw();
		setupTextWidgetLayoutData();
		validateFilterRegex();
		refilter();
	}

	private void refilter() {
		BubbleTextBoxChangeListener[] listeners = bubbleTextBoxChangeListeners.toArray(
				new BubbleTextBoxChangeListener[0]);
		for (BubbleTextBoxChangeListener listener : listeners) {
			listener.bubbleTextBoxChanged(this);
		}
	}

	public boolean isRegexEnabled() {
		return regexEnabled;
	}

	public void addBubbleTextBoxChangeListener(
			BubbleTextBoxChangeListener listener) {
		if (bubbleTextBoxChangeListeners.contains(listener)) {
			return;
		}
		bubbleTextBoxChangeListeners.add(listener);
		listener.bubbleTextBoxChanged(this);
	}

	public void removeBubbleTextBoxChangeListenener(
			BubbleTextBoxChangeListener listener) {
		bubbleTextBoxChangeListeners.remove(listener);
	}

	public void validateFilterRegex() {
		Color old_bg = (Color) textWidget.getData("TVSWTC:filter.bg");
		if (old_bg == null) {
			old_bg = textWidget.getBackground();
			textWidget.setData("TVSWTC:filter.bg", old_bg);
		}
		Color old_fg = (Color) textWidget.getData("TVSWTC:filter.fg");
		if (old_fg == null) {
			old_fg = textWidget.getForeground();
			textWidget.setData("TVSWTC:filter.fg", old_fg);
		}
		boolean old = regexIsError;
		if (regexEnabled) {
			if (FONT_NO_REGEX == null) {
				Font font = textWidget.getFont();

				Display display = textWidget.getDisplay();
				FontData[] fd = font.getFontData();
				for (int i = 0; i < fd.length; i++) {
					fd[i].setStyle(SWT.NORMAL);
				}
				FONT_NO_REGEX = new Font(display, fd);

				fd = FONT_NO_REGEX.getFontData();
				for (int i = 0; i < fd.length; i++) {
					fd[i].setStyle(SWT.BOLD);
				}
				FONT_REGEX = new Font(display, fd);

				Font monospaceFont = FontUtils.getMonospaceFont(display,
						fd[0].getHeight());
				if (monospaceFont == null) {
					for (int i = 0; i < fd.length; i++) {
						fd[i].setStyle(SWT.ITALIC);
					}
					FONT_REGEX_ERROR = new Font(display, fd);
				} else {
					FONT_REGEX_ERROR = monospaceFont;
				}

				textWidget.addDisposeListener((e) -> {
					FONT_NO_REGEX.dispose();
					FONT_REGEX.dispose();
					FONT_REGEX_ERROR.dispose();
				});
			}

			try {
				Pattern.compile(text,
						java.util.regex.Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
				regexIsError = false;

				textWidget.setBackground(COLOR_FILTER_REGEX);
				textWidget.setForeground(Colors.getInstance().getReadableColor(COLOR_FILTER_REGEX));
				textWidget.setFont(FONT_REGEX);
			} catch (Exception e) {
				regexIsError = true;
				regexError = e.getMessage();

				//textWidget.setBackground(Colors.colorErrorBG);
				textWidget.setBackground(old_bg);
				textWidget.setForeground(old_fg);
				textWidget.setFont(FONT_REGEX_ERROR);
			}
		} else {
			regexIsError = false;

			Color bg = text == null || text.isEmpty() ? old_bg
					: COLOR_FILTER_NO_REGEX;
			Color fg = bg == COLOR_FILTER_NO_REGEX
					? Colors.getInstance().getReadableColor(bg) : old_fg;
			textWidget.setBackground(bg);
			textWidget.setForeground(fg);
			if (FONT_NO_REGEX != null) {
				textWidget.setFont(FONT_NO_REGEX);
			}
		}
		if (old != regexIsError) {
			cBubble.redraw();
		}
		if (!text.isEmpty() && !regexIsError) {
			// in case TT is already being displayed, clear it because it's annoying
			cBubble.setToolTipText(null);
			textWidget.setToolTipText(null);
		}
	}

	public void setText(String s) {
		if (s.equals(text)) {
			return;
		}
		textWidget.setText(s);
	}

	public String getText() {
		return text;
	}

	public void setSelection(int start) {
		textWidget.setSelection(start);
	}

	public void setKeyListener(KeyListener keyListener) {
		this.keyListener = keyListener;
	}

	public KeyListener getKeyListener() {
		return keyListener;
	}

	public void setMessage(String message) {
		textWidget.setMessage(message);
	}

	public void setTooltip(String tooltip) {
		this.tooltip = tooltip;
	}
}
