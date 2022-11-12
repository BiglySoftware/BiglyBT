/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.shells;

import java.util.Iterator;
import java.util.LinkedHashMap;

import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Utils;

/**
 * Cheap ugly slider shell
 *
 * @author TuxPaper
 * @created Jul 5, 2007
 *
 */
public class SpeedScaleShell
{
	private static final boolean MOUSE_ONLY_UP_EXITS = true;
	private static final int AD_ACCEPT_DELAY = 500;
	
	private int OPTION_HEIGHT = 15;

	private int TEXT_HEIGHT = 32;

	private int SCALER_HEIGHT = 20;

	private int HEIGHT = TEXT_HEIGHT + SCALER_HEIGHT;

	private int MIN_WIDTH = 130;

	private int PADDING_X0 = 10;

	private int PADDING_X1 = 10;

	private int MARKER_HEIGHT = 10;

	private int MARKER_WIDTH = 5;

	private int PX_5 = 5;

	private int PX_2 = 2;

	private int PX_10 = 10;

	private static final int TYPED_TEXT_ALPHA = 180;

	private static final long CLOSE_DELAY = 600;

	private int WIDTH;

	private int WIDTH_NO_PADDING;

	private int value;

	private boolean cancelled;

	private int minValue;

	private int maxValue;

	private int maxTextValue;

	private int pageIncrement;

	private int bigPageIncrement;

	private Shell shell;

	private Shell parentShell;

	private LinkedHashMap mapOptions = new LinkedHashMap();

	private String sValue = "";

	private Composite composite;

	private boolean menuChosen;

	protected boolean lastMoveHadMouseDown;

	private boolean assumeInitiallyDown;

	private TimerEventPerformer cursorBlinkPerformer = null;

	private TimerEvent cursorBlinkEvent = null;

	public SpeedScaleShell() {
		minValue = 0;
		maxValue = -1;
		maxTextValue = -1;
		pageIncrement = 10;
		bigPageIncrement = 100;
		cancelled = true;
		menuChosen = false;
	}

	/**
	 * Borks with 0 or -1 maxValue
	 * @param cClickedFrom
	 *
	 * @param startValue
	 * @param _assumeInitiallyDown
	 * @return
	 *
	 * @since 3.0.1.7
	 */
	public boolean open(final Control cClickedFrom, final int startValue,
			boolean _assumeInitiallyDown) {
		long openTime = SystemTime.getMonotonousTime();
		value = startValue;
		this.assumeInitiallyDown = _assumeInitiallyDown;
		if (assumeInitiallyDown) {
			lastMoveHadMouseDown = true;
		}
		cancelled = true;

		shell = new Shell(parentShell == null ? Utils.findAnyShell() : parentShell,
				SWT.DOUBLE_BUFFERED | SWT.ON_TOP);
		shell.setLayout(new FillLayout());
		final Display display = shell.getDisplay();

		composite = new Composite(shell, SWT.DOUBLE_BUFFERED);

		GC gc = new GC(composite);
		gc.setAntialias(SWT.ON);
		WIDTH = MIN_WIDTH;
		Rectangle r = new Rectangle(0, 0, 9999, 20);
		for (Iterator iter = mapOptions.keySet().iterator(); iter.hasNext();) {
			Integer value = (Integer) iter.next();
			String text = (String) mapOptions.get(value);

			String s = getStringValue(value, text);
			GCStringPrinter stringPrinter = new GCStringPrinter(gc, s, r, 0, 0);
			stringPrinter.calculateMetrics();
			Point size = stringPrinter.getCalculatedSize();
			size.x *= 1.10;

			if (WIDTH < size.x) {
				WIDTH = size.x;
			}
		}
		gc.dispose();
		WIDTH_NO_PADDING = WIDTH - PADDING_X0 - PADDING_X1;

		final Point firstMousePos = display.getCursorLocation();

		composite.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					setCancelled(true);
					shell.dispose();
				} else if (e.detail == SWT.TRAVERSE_ARROW_NEXT) {
					setValue(value + 1);
				} else if (e.detail == SWT.TRAVERSE_ARROW_PREVIOUS) {
					setValue(value - 1);
				} else if (e.detail == SWT.TRAVERSE_PAGE_NEXT) {
					setValue(value + bigPageIncrement);
				} else if (e.detail == SWT.TRAVERSE_PAGE_PREVIOUS) {
					setValue(value - bigPageIncrement);
				} else if (e.detail == SWT.TRAVERSE_RETURN) {
					setCancelled(false);
					shell.dispose();
				}
			}
		});

		composite.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.PAGE_DOWN && e.stateMask == 0) {
					setValue(value + pageIncrement);
				} else if (e.keyCode == SWT.PAGE_UP && e.stateMask == 0) {
					setValue(value - pageIncrement);
				} else if (e.keyCode == SWT.HOME) {
					setValue(minValue);
				} else if (e.keyCode == SWT.END) {
					if (maxValue != -1) {
						setValue(maxValue);
					}
				}
			}
		});

		final MouseMoveListener mouseMoveListener = new MouseMoveListener() {
			private MouseEvent pending_event;

			@Override			
			public void mouseMove(MouseEvent e) {
				long now = SystemTime.getMonotonousTime();
				long rem = now - openTime;
				if ( assumeInitiallyDown &&  rem < AD_ACCEPT_DELAY ){
					pending_event = e;
					SimpleTimer.addEvent("updater",
							SystemTime.getOffsetTime(CLOSE_DELAY), new TimerEventPerformer() {
						@Override
						public void perform(TimerEvent event) {
							Utils.execSWTThread(new AERunnable() {
								@Override
								public void runSupport() {
									if ( pending_event == e ){
										pending_event = null;
										if ( !composite.isDisposed()){
											mouseMove(e);
										}
									}
								}
							});
						}
					});
					
					return;
				}
				pending_event = null;
				Point ptOnDisplay = ((Control) e.widget).toDisplay(e.x, e.y);
				Point ptOnComposite = composite.toControl(ptOnDisplay);
				lastMoveHadMouseDown = false;
				boolean hasButtonDown = (e.stateMask & SWT.BUTTON_MASK) > 0
						|| assumeInitiallyDown;
				if (hasButtonDown) {
					if (ptOnComposite.y > HEIGHT - SCALER_HEIGHT) {
						lastMoveHadMouseDown = true;
						setValue(getValueFromMousePos(ptOnComposite.x));
					}
					composite.redraw();
				} else {
					composite.redraw();
				}
			}
		};

		composite.addMouseMoveListener(mouseMoveListener);

		composite.addMouseTrackListener(new MouseTrackListener() {
			boolean mouseIsOut = false;

			private boolean exitCancelled = false;

			@Override
			public void mouseHover(MouseEvent e) {
			}

			@Override
			public void mouseExit(MouseEvent e) {
				if (composite.equals(Utils.getCursorControl())) {
					return;
				}
				mouseIsOut = true;
				SimpleTimer.addEvent("close scaler",
						SystemTime.getOffsetTime(CLOSE_DELAY), new TimerEventPerformer() {
					@Override
					public void perform(TimerEvent event) {
						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								if (!exitCancelled) {
									shell.dispose();
								} else {
									exitCancelled = false;
								}
							}
						});
					}
				});
			}

			@Override
			public void mouseEnter(MouseEvent e) {
				if (mouseIsOut) {
					exitCancelled = true;
				}
				mouseIsOut = false;
			}
		});

		final MouseListener mouseListener = new MouseListener() {
			boolean bMouseDown = false;

			@Override
			public void mouseUp(MouseEvent e) {
				boolean temp = assumeInitiallyDown;
				Point ptOnDisplay = ((Control) e.widget).toDisplay(e.x, e.y);
				Point ptOnComposite = composite.toControl(ptOnDisplay);
				if (assumeInitiallyDown && e.widget == composite) {
					//System.out.println("assumed down");
					assumeInitiallyDown = false;
				}
				if (MOUSE_ONLY_UP_EXITS) {
					//System.out.println("last move had mouse down: " + lastMoveHadMouseDown);
					if (lastMoveHadMouseDown) {
						Point mousePos = display.getCursorLocation();
						//System.out.println("first=" + firstMousePos + ";mouse= " + mousePos);
						if (mousePos.equals(firstMousePos)) {
							return;
						}
					}
					bMouseDown = true;
				}
				if (bMouseDown) {
					long now = SystemTime.getMonotonousTime();
					if ( temp && now - openTime < AD_ACCEPT_DELAY ){
						return;
					}
					
					if (ptOnComposite.y > HEIGHT - SCALER_HEIGHT) {
						setValue(getValueFromMousePos(ptOnComposite.x));
						setCancelled(false);
						if (lastMoveHadMouseDown) {
							shell.dispose();
						}
					} else if (ptOnComposite.y > TEXT_HEIGHT) {
						int idx = (ptOnComposite.y - TEXT_HEIGHT) / OPTION_HEIGHT;
						Iterator iterator = mapOptions.keySet().iterator();
						int newValue;
						do {
							newValue = ((Integer) iterator.next()).intValue();
							idx--;
						} while (idx >= 0);
						value = newValue; // ignore min/max
						setCancelled(false);
						setMenuChosen(true);
						shell.dispose();
					}
				}
			}

			@Override
			public void mouseDown(MouseEvent e) {
				Point ptOnDisplay = ((Control) e.widget).toDisplay(e.x, e.y);
				Point ptOnComposite = composite.toControl(ptOnDisplay);
				if (e.count > 1) {
					lastMoveHadMouseDown = true;
					return;
				}
				Point mousePos = display.getCursorLocation();
				if (ptOnComposite.y > HEIGHT - SCALER_HEIGHT) {
					bMouseDown = true;
					setValue(getValueFromMousePos(e.x));
				}
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
			}

		};
		composite.addMouseListener(mouseListener);
		if (cClickedFrom != null) {
			cClickedFrom.addMouseListener(mouseListener);
			cClickedFrom.addMouseMoveListener(mouseMoveListener);
			composite.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent arg0) {
					cClickedFrom.removeMouseListener(mouseListener);
					cClickedFrom.removeMouseMoveListener(mouseMoveListener);
				}
			});
		}

		composite.addPaintListener(new PaintListener() {
			@Override
			public void paintControl(PaintEvent e) {
				int valueRange = maxValue - minValue;
				int x = WIDTH_NO_PADDING * (value - minValue) / valueRange;
				if (x < 0) {
					x = 0;
				} else if (x > WIDTH_NO_PADDING) {
					x = WIDTH_NO_PADDING;
				}
				int startX = WIDTH_NO_PADDING * (startValue - minValue) / valueRange;
				if (startX < 0) {
					startX = 0;
				} else if (startX > WIDTH_NO_PADDING) {
					startX = WIDTH_NO_PADDING;
				}
				int baseLinePos = getBaselinePos();

				try {
					e.gc.setAdvanced(true);
					e.gc.setAntialias(SWT.ON);
				} catch (Exception ex) {
					// aw
				}

				e.gc.setLineWidth(1);

				e.gc.setForeground(
						Colors.getSystemColor(display, SWT.COLOR_WIDGET_NORMAL_SHADOW));
				// left
				e.gc.drawLine(PADDING_X0, baseLinePos - 6, PADDING_X0, baseLinePos + 6);
				// right
				e.gc.drawLine(PADDING_X0 + WIDTH_NO_PADDING, baseLinePos - 6,
						PADDING_X0 + WIDTH_NO_PADDING, baseLinePos + 6);
				// baseline
				e.gc.drawLine(PADDING_X0, baseLinePos, PADDING_X0 + WIDTH_NO_PADDING,
						baseLinePos);

				e.gc.setForeground(Colors.getSystemColor(display, SWT.COLOR_WIDGET_FOREGROUND));
				e.gc.setBackground(Colors.getSystemColor(display, SWT.COLOR_WIDGET_FOREGROUND));
				// start value marker
				e.gc.drawLine(PADDING_X0 + startX, baseLinePos - PX_5,
						PADDING_X0 + startX, baseLinePos + PX_5);
				// current value marker
				e.gc.fillRoundRectangle(PADDING_X0 + x - PX_2, baseLinePos - PX_5,
						MARKER_WIDTH, MARKER_HEIGHT, MARKER_HEIGHT, MARKER_HEIGHT);

				// Current Value Text
				e.gc.setForeground(Colors.getSystemColor(display, SWT.COLOR_INFO_FOREGROUND));
				e.gc.setBackground(Colors.getSystemColor(display, SWT.COLOR_INFO_BACKGROUND));

				e.gc.fillRectangle(0, 0, WIDTH, TEXT_HEIGHT);

				GCStringPrinter.printString(e.gc, _getStringValue(),
						new Rectangle(0, 0, WIDTH, HEIGHT), true, false,
						SWT.CENTER | SWT.TOP | SWT.WRAP);

				e.gc.drawLine(0, TEXT_HEIGHT, WIDTH, TEXT_HEIGHT);

				// options list
				int y = TEXT_HEIGHT;
				Point mousePos = composite.toControl(display.getCursorLocation());
				for (Iterator iter = mapOptions.keySet().iterator(); iter.hasNext();) {
					Integer value = (Integer) iter.next();
					String text = (String) mapOptions.get(value);

					e.gc.setAntialias(SWT.ON);
					Rectangle area = new Rectangle(0, y, WIDTH, OPTION_HEIGHT);
					Color bg;
					if (area.contains(mousePos)) {
						bg = Colors.getSystemColor(display, SWT.COLOR_LIST_SELECTION);
						e.gc.setBackground(bg);
						e.gc.setForeground(
								Colors.getSystemColor(display, SWT.COLOR_LIST_SELECTION_TEXT));
						e.gc.fillRectangle(area);
					} else {
						bg = Colors.getSystemColor(display, SWT.COLOR_LIST_BACKGROUND);
						e.gc.setBackground(bg);
						e.gc.setForeground(
								Colors.getSystemColor(display, SWT.COLOR_LIST_FOREGROUND));
					}

					int ovalGap = 6;
					float ovalPadding = ovalGap / 2.0f;
					int ovalSize = OPTION_HEIGHT - ovalGap;
					float xCenter = (ovalSize / 2.0f) + PX_2;
					float yCenter = (ovalSize / 2.0f) + ovalPadding;
					if (getValue() == value.intValue()) {
						Color saveColor = e.gc.getBackground();
						e.gc.setBackground(e.gc.getForeground());
						float ovalSizeMini = ovalSize - (ovalGap / 2.0f);
						int xMiniOval = (int) Math.round((xCenter - (ovalSizeMini / 2.0)));
						int yMiniOval = (int) Math.round(yCenter - (ovalSizeMini / 2.0));
						e.gc.fillOval(xMiniOval, y + yMiniOval, Math.round(ovalSizeMini),
								Math.round(ovalSizeMini));
						e.gc.setBackground(saveColor);
					}
					if (Constants.isLinux) {
						// Hack: on linux, drawing oval seems to draw a line from last pos
						// to start of oval.. drawing a point (anywhere) seems to clear the
						// path
						Color saveColor = e.gc.getForeground();
						e.gc.setForeground(bg);
						e.gc.drawPoint(PX_2, (int) (y + ovalPadding));
						e.gc.setForeground(saveColor);
					}
					e.gc.drawOval(PX_2, (int) (y + ovalPadding), ovalSize, ovalSize);

					GCStringPrinter.printString(e.gc, text, new Rectangle(OPTION_HEIGHT,
							y, WIDTH - OPTION_HEIGHT, OPTION_HEIGHT), true, false, SWT.LEFT);
					y += OPTION_HEIGHT;
				}

				// typed value
				if (sValue.length() > 0) {
					Point extent = e.gc.textExtent(sValue);
					if (extent.x > WIDTH - PX_10) {
						extent.x = WIDTH - PX_10;
					}
					int yTypedValue = 15;
					Rectangle rect = new Rectangle(WIDTH - (PX_10 - 2) - extent.x, yTypedValue - 1,
							extent.x + PX_5,
							extent.y + (PX_5 - 1) + (yTypedValue - 1) > TEXT_HEIGHT
									? TEXT_HEIGHT - yTypedValue : extent.y + (PX_5 - 1));
					e.gc.setBackground(Colors.getSystemColor(display, SWT.COLOR_INFO_BACKGROUND));
					e.gc.fillRectangle(rect);

					try {
						e.gc.setAlpha(TYPED_TEXT_ALPHA);
					} catch (Exception ex) {
					}
					//e.gc.drawRectangle(rect);
					e.gc.setForeground(Colors.getSystemColor(display, SWT.COLOR_INFO_FOREGROUND));

					GCStringPrinter.printString(e.gc, sValue, new Rectangle(rect.x + PX_2,
							rect.y + PX_2, WIDTH - PX_5, OPTION_HEIGHT), true, false,
							SWT.LEFT | SWT.BOTTOM);
				}
			}
		});

		// blinking cursor so people know they can type
		final AERunnable cursorBlinkRunnable = new AERunnable() {
			boolean on = false;

			@Override
			public void runSupport() {
				if (composite.isDisposed()) {
					return;
				}

				on = !on;

				GC gc = new GC(composite);
				try {
					gc.setLineWidth(PX_2);
					if (!on) {
						gc.setForeground(Colors.getSystemColor(display, SWT.COLOR_INFO_BACKGROUND));
					} else {
						try {
							gc.setForeground(
									Colors.getSystemColor(display, SWT.COLOR_INFO_FOREGROUND));
							gc.setAlpha(TYPED_TEXT_ALPHA);
						} catch (Exception e) {
						}
					}
					int y = 15;
					gc.drawLine(WIDTH - PX_5, y + 1, WIDTH - PX_5, y + OPTION_HEIGHT);
				} finally {
					gc.dispose();
				}
				if (cursorBlinkPerformer != null) {
					cursorBlinkEvent = SimpleTimer.addEvent("BlinkingCursor",
							SystemTime.getOffsetTime(500), cursorBlinkPerformer);
				}
			}
		};
		cursorBlinkPerformer = new TimerEventPerformer() {
			@Override
			public void perform(final TimerEvent event) {
				Utils.execSWTThread(cursorBlinkRunnable);
			}
		};
		cursorBlinkEvent = SimpleTimer.addEvent("BlinkingCursor",
				SystemTime.getOffsetTime(500), cursorBlinkPerformer);

		composite.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (Character.isDigit(e.character)) {
					sValue += e.character;
				} else if (e.keyCode == SWT.BS && sValue.length() > 0) {
					sValue = sValue.substring(0, sValue.length() - 1);
				} else {
					return;
				}
				try {
					int newValue = Integer.parseInt(sValue);
					if (maxTextValue == -1) {
						setValue(newValue);
					} else {
						if (minValue > 0 && newValue < minValue) {
							newValue = minValue;
						}
						if (newValue > maxTextValue) {
							newValue = maxTextValue;
						}
						value = newValue;
						composite.redraw();
					}
				} catch (Exception ex) {
					setValue(startValue);
				}
			}
		});

		Point location = display.getCursorLocation();

		location.y -= getBaselinePos();
		int x = (int) (WIDTH_NO_PADDING * (value > maxValue ? 1
				: ((double) value / maxValue) - (double) minValue));
		location.x -= PADDING_X0 + x;

		Rectangle bounds = new Rectangle(location.x, location.y, WIDTH, HEIGHT);
		Monitor mouseMonitor = shell.getMonitor();
		Monitor[] monitors = display.getMonitors();
		for (int i = 0; i < monitors.length; i++) {
			Monitor monitor = monitors[i];
			if (monitor.getBounds().contains(location)) {
				mouseMonitor = monitor;
				break;
			}
		}
		Rectangle monitorBounds = mouseMonitor.getBounds();
		Rectangle intersection = monitorBounds.intersection(bounds);
		if (intersection.width != bounds.width) {
			bounds.x = monitorBounds.x + monitorBounds.width - WIDTH;
			bounds.width = WIDTH;
		}
		if (intersection.height != bounds.height) {
			bounds.y = monitorBounds.y + monitorBounds.height - HEIGHT;
			bounds.height = HEIGHT;
		}

		shell.setBounds(bounds);
		if (!bounds.contains(firstMousePos)) {
			// should never happen, which means it probably will, so handle it badly
			shell.setLocation(firstMousePos.x - (bounds.width / 2),
					firstMousePos.y - bounds.height + 2);
		}

		shell.open();
		// must be after, for OSX
		composite.setFocus();

		try {
			Utils.readAndDispatchLoop( shell );

		} catch (Throwable t) {
			Debug.out(t);
		}

		if (cursorBlinkEvent != null) {
			cursorBlinkEvent.cancel();
			cursorBlinkEvent = null;
		}

		return !cancelled;
	}

	/**
	 * @param x
	 * @return
	 *
	 * @since 3.0.1.7
	 */
	protected int getValueFromMousePos(int x) {
		int x0 = x + 1;
		if (x < PADDING_X0) {
			x0 = PADDING_X0;
		} else if (x > PADDING_X0 + WIDTH_NO_PADDING) {
			x0 = PADDING_X0 + WIDTH_NO_PADDING;
		}

		return ((x0 - PADDING_X0) * (maxValue - minValue) / WIDTH_NO_PADDING)
				+ minValue;
	}

	public int getValue() {
		return value;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	public int getMinValue() {
		return minValue;
	}

	public void setMinValue(int minValue) {
		this.minValue = minValue;
	}

	public int getMaxValue() {
		return maxValue;
	}

	public void setMaxValue(int maxValue) {
		this.maxValue = maxValue;
	}

	public void setValue(int value) {
		//System.out.println("sv " + value + ";" + Debug.getCompressedStackTrace());
		if (value > maxValue) {
			value = maxValue;
		} else if (value < minValue) {
			value = minValue;
		}
		this.value = value;
		if (composite != null && !composite.isDisposed()) {
			composite.redraw();
		}
	}

	public String _getStringValue() {
		String name = (String) mapOptions.get(new Integer(value));
		return getStringValue(value, name);
	}

	public String getStringValue(int value, String sValue) {
		if (sValue != null) {
			return sValue;
		}
		return "" + value;
	}

	private int getBaselinePos() {
		return HEIGHT - (SCALER_HEIGHT / 2);
	}

	public void addOption(String id, int value) {
		mapOptions.put(new Integer(value), id);
		HEIGHT += OPTION_HEIGHT;
	}

	public int getMaxTextValue() {
		return maxTextValue;
	}

	public void setMaxTextValue(int maxTextValue) {
		this.maxTextValue = maxTextValue;
	}

	public boolean wasMenuChosen() {
		return menuChosen;
	}

	public void setMenuChosen(boolean menuChosen) {
		this.menuChosen = menuChosen;
	}

	public void setParentShell(Shell parentShell) {
		this.parentShell = parentShell;
	}
}
