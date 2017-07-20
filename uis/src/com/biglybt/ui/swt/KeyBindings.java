/*
 * File    : KeyBindings.java
 * Created : 2005 Jan 7
 * By      : James Yeh
 *
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

package com.biglybt.ui.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MenuItem;
import com.biglybt.core.util.Constants;
import com.biglybt.core.internat.MessageText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * Facilitates localization-specific and platform-specific keyboard shortcut handling through the use of keybinding values.
 *</p>
 * <p>
 * A keybinding value is a line of String that can be specified in the localization bundle properties file for a particular menu item. To do so,
 *  a localization key/value pair is used, with the key being the same as menu item's key plus ".keybinding".
 * </p>
 * <p>
 * For instance, if a keyboard shortcut needs to be specified for "MainWindow.menu.file.open", then a key/value pair with the key of
 *  "MainWindow.menu.file.open.keybinding" is created. The value is what would be used as the keyboard accelerator, with the following special
 *  values:</br>
 * <ul>
 * <li>Meta (or Cmd) - The "Meta" modifier; this is "Command" on OS X, and "Control" on all other platforms</li>
 * <li>Ctrl - The "Control" modifier on <b>all</b> platforms. Use this only if you need to enforce the use of Control.</li>
 * <li>Alt (or Opt) - The "Alt" modifier</li>
 * <li>Shift - The "Shift" modifier</li>
 * <li>Ins - Insert</li>
 * <li>Backspace</li>
 * <li>Del - Delete</li>
 * <li>Esc - Escape</li>
 * <li>PgUp - Page Up</li>
 * <li>PgDn - Page Down</li>
 * <li>Left - The left arrow</li>
 * <li>Up - The up arrow</li>
 * <li>Right - The right arrow</li>
 * <li>Down - The down arrow</li>
 * <li>Home</li>
 * <li>End</li>
 * <li>Tab</li>
 * <li>Fx, where x is an integer from 1 to 15 (inclusive) - The function key (F1-F15)</li>
 * </ul><br />
 * Other valid values can be typed as is or as the Unicode representation. For security reasons, this is initially set with a very conservative scope,
 *  including alphanumerics, \, =, -, (comma), (period), and `.
 * </p>
 * <p>
 * As of version 1.2, keybindings are only set if the following conditions are met:
 * <ol>
 * <li>A function key is set <i>or</i><li>
 * <li>Meta, Alt, or Ctrl is set (or their variations)</li>
 * </ol>
 * </p>
 * <p>
 * The keys were chosen to more conveniently address the issue on platforms like Windows where vanilla SWT does not display the shortcuts, as
 *  opposed to higher-level API like JFace or some platforms' native rendering.
 * </p>
 * <p>
 * For example, if File / Open / .torrent File is set to be Meta+O (Command+O or Ctrl+O), then in MessageBundle.properties (or the localization-
 * specific equivalent), MainWindow.menu.file.open.torrent.keybinding=Meta+O will be entered. The label will be adjusted to Ctrl on non-OS X
 * platforms (OS X will draw the glyph for Cmd).
 * </p>
 * <p>
 * As another example, if File / Exit is set to be Alt+F4, then in MessageBundle.properties (or the localization-specific equivalent),
 *  MainWindow.menu.file.exit.keybinding=Alt+F4 will be entered.
 * </p>
 * <p>
 * To accommodate for the variety of locales and platforms running on the client, platforms and localizations can "override" the default keybinding value.
 *  The order of parsing is as follows:<br />
 * <ol>
 * <li>If a localized keybinding value exists for the current locale and platform, it is used</li>
 * <li>If the above is not found, this method looks for a keybinding value for the current locale without platform specificity</li>
 * <li>If the above is not found, this method looks for a keybinding value for the default locale and the currently running platform</li>
 * <li>If the above is not found, this method looks for a keybinding value for the default locale without platform specificity</li>
 * <li>If the above is not found, no accelerator is set for the MenuItem</li>
 * </ol>
 * </p>
 * <p>
 * For instance, to refer to the above example, if the Mac OS X target for the client wants to handle File / Exit as Command+Q, then
 *  MainWindow.menu.file.exit.keybinding.mac=Meta+Q is entered. If it is not entered, the value for the 'default' key MainWindow.menu.file.exit.keybinding
 *  will be used.
 * </p>
 * <p>
 * The platform suffix can be attached to the end of the localization key. Valid suffixes are:<br/>
 * <ul>
 * <li>.linux - Linux</li>
 * <li>.mac - Mac OS X</li>
 * <li>.windows - Windows</li>
 * </ul>
 * </p>
 * @author CrazyAlchemist
 * @version 1.3 Added removeAccelerator
 */
public final class KeyBindings
{
    private static final Pattern FUNC_EXP = Pattern.compile("([fF]{1})([1-9]{1}[0-5]{0,1})");
    private static final Pattern SANCTIONED_EXP = Pattern.compile("([ a-zA-Z\\d/\\\\=\\-,\\.`]{1})");

    // modifier key/value pairs must be symmetrical
    private static final String[] SPECIAL_KEYS = new String[] {
        "Meta",
        "Ctrl",
        "Cmd",
        "Alt",
        "Opt",
        "Shift",
        "Ins",
        "Backspace",
        "Del",
        "Esc",
        "PgUp",
        "PgDn",
        "Left",
        "Up",
        "Right",
        "Down",
        "Home",
        "End",
        "Tab"
    };

    private static final int[] SPECIAL_VALUES = new int[] {
        SWT.MOD1,
        SWT.CTRL,
        SWT.MOD1,
        SWT.ALT,
        SWT.ALT,
        SWT.SHIFT,
        SWT.INSERT,
        '\010', // backspace
        '\u007f', // del
        '\033',
        SWT.PAGE_UP,
        SWT.PAGE_DOWN,
        SWT.LEFT,
        SWT.UP,
        SWT.RIGHT,
        SWT.DOWN,
        SWT.HOME,
        SWT.END,
        '\t'
    };

    private static final String DELIM = "+";
    private static final String DELIM_EXP = "\\+";

    /**
     * <p>
     * Gets the localization key suffix for the running platform for keybinding retrieval
     * </p>
     * <p>
     * For now, as is with the client's Constants behaviour, supported platforms are Linux, Mac OS X, and Windows only
     * </p>
     * @return The platform key suffix; or an empty string on an unsupported platform
     */
    private static String getPlatformKeySuffix()
    {
        if(Constants.isLinux)
            return ".linux";
        else if(Constants.isSolaris)
          return ".solaris";
        else if(Constants.isUnix)
          return ".unix";
        else if(Constants.isFreeBSD)
          return ".freebsd";
        else if(Constants.isOSX)
            return ".mac";
        else if(Constants.isWindows)
            return ".windows";

        return "";
    }

    /**
     * Parses the keybinding string according to the specifications documented at this class and gets the SWT value equivalent for keyboard accelerator settings.
     * @param keyBindingValue Keybinding value
     * @return A KeyBindingInfo object, which consists of the SWT accelerator and its display name
     */
    private static KeyBindingInfo parseKeyBinding(final String keyBindingValue)
    {
        if(keyBindingValue.length() < 1)
            return new KeyBindingInfo(null, SWT.NONE);

        // initialize with nothing
        int swtAccelerator = SWT.NONE;

        final String[] tmpValues = keyBindingValue.split(DELIM_EXP);
        final boolean[] specVisited = new boolean[SPECIAL_KEYS.length]; // flag for speed optimization
        boolean funcVisited = false;

        final StringBuilder displayValue = new StringBuilder(keyBindingValue.length() + 2); // allocate display string
        displayValue.append('\t');

        for (int i = 0; i < tmpValues.length; i++)
        {
            final String value = tmpValues[i];
            boolean matched = false;

             // process special keys first
            for(int j = 0; j < SPECIAL_KEYS.length; j++)
            {
                if(!specVisited[j] && SPECIAL_KEYS[j].equalsIgnoreCase(value))
                {
                    swtAccelerator = swtAccelerator | SPECIAL_VALUES[j];

                    // special-case meta; a generalized solution would be warranted if:
                    // a) additional special modifiers persist or
                    // b) other platforms have special labeling requirements or
                    // c) SWT changes its lower-level API so shortcut labels are no longer custom drawn on Win etc.
                    if(SPECIAL_KEYS[j].equalsIgnoreCase("Meta"))
                        displayValue.append(Constants.isOSX ? "Cmd" : "Ctrl").append(DELIM);
                    else
                        displayValue.append(SPECIAL_KEYS[j]).append(DELIM);

                    // mark flags
                    specVisited[j] = true;
                    matched = true;
                    break;
                }
            }

            if(matched)
                continue;

            // special treatment for function keys
            if(!funcVisited)
            {
                final Matcher funcMatcher = FUNC_EXP.matcher(value);
                if(funcMatcher.find() && funcMatcher.start() == 0 && funcMatcher.end() == value.length())
                {
                    final int funcVal = Integer.parseInt(funcMatcher.group(2));

                    // SWT.F1 is (1 << 24) + 10
                    swtAccelerator = swtAccelerator | ((1 << 24) + (9 + funcVal));
                    displayValue.append(funcMatcher.group(0)).append(DELIM);

                    funcVisited = true;
                    matched = true;
                }
            }

            if(matched)
                continue;

            final Matcher valMatcher = SANCTIONED_EXP.matcher(value);
            if(valMatcher.find() && valMatcher.start() == 0)
            {
                final char c = valMatcher.group().charAt(0);

                // avoid possible duplicates (\t is index 0)
                final int subStrIndex = displayValue.indexOf(c + DELIM);
                if(subStrIndex == 1 || (subStrIndex > 1 && displayValue.substring(subStrIndex - 1, subStrIndex).equals(DELIM)))
                    continue;

                swtAccelerator = swtAccelerator | c;
                displayValue.append(c).append(DELIM);
            }
        }

        if(funcVisited || specVisited[0] || specVisited[1] || specVisited[2] || specVisited[3] || specVisited[4]) // special case - be a bit careful for now
            return new KeyBindingInfo(displayValue.substring(0, displayValue.length() - 1), swtAccelerator);
        else
            return new KeyBindingInfo(null, SWT.NONE);
    }

    /**
     * <p>
     * Removes the keyboard accelerator for a SWT MenuItem
     * </p>
     * @param menu SWT MenuItem
     * @param localizationKey The MenuItem's localization key for the localization resource bundle
     */
    public static void removeAccelerator(final MenuItem menu, String localizationKey)
    {
        setAccelerator(menu, new KeyBindingInfo("", SWT.NONE));
        Messages.setLanguageText(menu, localizationKey);
    }

    /**
     * <p>
     * Sets the keyboard accelerator for a SWT MenuItem.
     * </p>
     * <p>
     * There is a specific order of accelerator setting in consideration with different platforms and localizations. Specifically:<br />
     * <ol>
     * <li>If a localized keybinding value exists for the current locale and platform, it is used</li>
     * <li>If the above is not found, this method looks for a keybinding value for the current locale without platform specificity</li>
     * <li>If the above is not found, this method looks for a keybinding value for the default locale and the currently running platform</li>
     * <li>If the above is not found, this method looks for a keybinding value for the default locale without platform specificity</li>
     * <li>If the above is not found, no accelerator is set for the MenuItem</li>
     * </ol>
     * </p>
     * @param menu SWT MenuItem
     * @param localizationKey The MenuItem's localization key for the localization resource bundle
     */
    public static void setAccelerator(final MenuItem menu, String localizationKey)
    {
        localizationKey += ".keybinding";
        final String platformSpecificKey = localizationKey + getPlatformKeySuffix();

        // first, check for platform-specific, localization-specific binding
        if(MessageText.keyExists(platformSpecificKey))
        {
            setAccelerator(menu, parseKeyBinding(MessageText.getString(platformSpecificKey)));
        }
        else if(MessageText.keyExists(localizationKey)) // platform-independent, localization-specific binding
        {
            setAccelerator(menu, parseKeyBinding(MessageText.getString(localizationKey)));
        }
        else if(!MessageText.isCurrentLocale(MessageText.LOCALE_DEFAULT))
        {
            // default locale

            // platform-specific first
            if(MessageText.keyExistsForDefaultLocale(platformSpecificKey))
            {
                setAccelerator(menu, parseKeyBinding(MessageText.getDefaultLocaleString(platformSpecificKey)));
            }
            else if(MessageText.keyExistsForDefaultLocale(localizationKey))
            {
                 // default locale, platform-independent
                setAccelerator(menu, parseKeyBinding(MessageText.getDefaultLocaleString(localizationKey)));
            }
        }
    }

    public static KeyBindingInfo getKeyBindingInfo( String localizationKey)
    {
        localizationKey += ".keybinding";
        final String platformSpecificKey = localizationKey + getPlatformKeySuffix();

        // first, check for platform-specific, localization-specific binding
        if(MessageText.keyExists(platformSpecificKey))
        {
            return( parseKeyBinding(MessageText.getString(platformSpecificKey)));
        }
        else if(MessageText.keyExists(localizationKey)) // platform-independent, localization-specific binding
        {
        	return( parseKeyBinding(MessageText.getString(localizationKey)));
        }
        else if(!MessageText.isCurrentLocale(MessageText.LOCALE_DEFAULT))
        {
            // default locale

            // platform-specific first
            if(MessageText.keyExistsForDefaultLocale(platformSpecificKey))
            {
            	return( parseKeyBinding(MessageText.getDefaultLocaleString(platformSpecificKey)));
            }
            else if(MessageText.keyExistsForDefaultLocale(localizationKey))
            {
                 // default locale, platform-independent
            	return(  parseKeyBinding(MessageText.getDefaultLocaleString(localizationKey)));
            }
        }

        return( null );
    }

    /**
     * Helper method to set a keyboard accelerator for a MenuItem. If kbInfo is SWT.NONE, no accelerator will be set.
     * @param menu SWT MenuItem
     * @param kbInfo KeyBindingInfo object, which contains the SWT accelerator value and its display name
     */
    private static void setAccelerator(final MenuItem menu, final KeyBindingInfo kbInfo)
    {
    	if ( menu.isDisposed()){
    		return;
    	}
        if(kbInfo.accelerator != SWT.NONE)
        {
            menu.setAccelerator(kbInfo.accelerator);

            // SWT on OS X now uses native drawing
            if(!Constants.isOSX && !menu.getText().endsWith(kbInfo.name))
                menu.setText(menu.getText() + kbInfo.name);
        }
    }

    /**
     * Runs simple tests on KeyBindings keybinding values
     * @param args Command-line arguments; they are not used
     */
    public static void main(final String[] args)
    {
        System.out.println(parseKeyBinding("Ctrl+1").name); // meta+1
        System.out.println(parseKeyBinding("Ctrl+F12").name); // meta+f12
        System.out.println(parseKeyBinding("Ctrl+F4").name); // meta+f4

        System.out.println("Meta+Shift+O");
        System.out.println(parseKeyBinding("Ctrl+Shift+O").accelerator); // meta+shift+o
        System.out.println(parseKeyBinding("Shift+Ctrl+O").accelerator); // meta+shift+o
        System.out.println(SWT.MOD1 | SWT.SHIFT | 'O'); // meta+shift+o

        System.out.println("Meta+Shift+o");
        System.out.println(SWT.MOD1 | SWT.SHIFT | 'o'); // meta+shift+o
    }

    /**
     * <p>
     * A basic bean object containing the SWT accelerator and its display name. This is because on platforms like Windows, vanilla SWT MenuItem must be
     * provided the textual representation (display name) of the accelerator in order for it to be visible to the users (as opposed to having it handled by a higher-
     * level API like JFace or native rendering).
     * </p>
     */
    public static class KeyBindingInfo
    {
        /**
         * The display name of the accelerator
         */
        public final String name;

        /**
         * The SWT keyboard accelerator value
         */
        public final int accelerator;

        /**
         * Constructs a new KeyBindingInfo object with the given accelerator name and accelerator value
         * @param name Display name
         * @param accelerator SWT accelerator value
         */
        private KeyBindingInfo(final String name, final int accelerator)
        {
            this.name = name;
            this.accelerator = accelerator;
        }
    }
}
