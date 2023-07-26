# Translating BiglyBT

Thank you for your interesting in helping out BiglyBT.  We really need help with translating BiglyBT to as many languages possible.  Translating BiglyBT is no small task.  We have over 4000 translation strings, and many of the 40 language we bundle are very incomplete.  A breakdown of the completion level of each language is displayed on our [BiglyBT CrowdIn](https://crowdin.com/project/biglybt) page:

Preferably, we'd love it if your native language wasn't English, however, anyone fluent in another language is very much appreciated.

## How to Translate

We highly recommend using Crowdin to translate, however, we will not refuse translations using your favorite translation tool.
The Language files are "Java Properties" files, located in [core/src/com/biglybt/internat](core/src/com/biglybt/internat).  

### Crowdin

Visit our [BiglyBT Project Page on Crowdin](https://crowdin.com/project/BiglyBT).  Crowdin is one of the best collaborative online localization tools, and has handy features such as suggesting translations from other projects with similar strings.

Please note that BiglyBT for Android has its own translation project at [BiglyBT-Android Project Page on Crowdin](https://crowdin.com/project/biglybt-android)


### BiglyBT Internationization Plugin (Deprecated)

Prior to using Crowdin for translation management, we had a BiglyBT plugin to handle translations.  Although it has been deprecated you can still install it - see [the Wiki](https://github.com/BiglySoftware/BiglyBT/wiki/Translating-BiglyBT) 

### IntelliJ

IntelliJ Idea IDE has an ok 2-panel translation tool, and there are many tutorials out there on how to setup IntelliJ with a GitHub project.  Once setup, you can open MessagesBundle.properties, and switch to the "Resource Bundle" tab.  Unfortunately it doesn't filter on just one language, so it can be a bit annoying to use.

## Submitting Translations

If you are using Crowdin, you don't have to submit translations beyond typing them into the crowdin interface.  The process to get the strings into BiglyBT is automated.

### Github Clone way

This requires you to know how to use GitHub.  Clone our repository, commit your new properties file into your clone, and do a Pull Request.

### Manual (Easiest Way)

The easiest way to submit your translation is to paste it into GitHub.  There are many steps, but they are pretty straight forward.

1. Open your changed .properties file and copy all of it to the clipboard
1. Go to [core/src/com/biglybt/internat](core/src/com/biglybt/internat)
1. Click on the language file you edited
1. Click the edit button
1. Select all the text
1. Paste your changes over top
1. Type a commit change title, and click the "Propose File Change" button
1. Fill in the rest of the information (See [Editing files in another user's repository](https://help.github.com/articles/editing-files-in-another-user-s-repository/) if you are stuck) and click "Create Pull Request"

### E-Mail (Slowest Way)

You can just e-mail us the new .properties file at info (at) biglybt (dot) com
