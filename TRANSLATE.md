# Translating BiglyBT

Thank you for your interesting in helping out BiglyBT.  We really need help with translating BiglyBT to as many languages possible.  Translating BiglyBT is no small task.  We have over 4000 translation strings, and many of the 40 language we bundle are very incomplete.  Here's a breakdown of the completion level of each language in v1.2.0.0:

| Locale | Language | Country | % Translated | Active Translator(s) |
|:---|:---|:---|---:|:---|
| pt_BR | Portuguese | Brazil | ~99% | [Havokdan](https://github.com/Havokdan) |
| eu | Basque |  | ~99% | azpidatziak |
| zh_CN | Chinese | China | ~88% |  |
| bg_BG | Bulgarian | Bulgaria | ~83% | andreshko |
| es_ES | Spanish | Spain | ~80% | Valtiel |
| ca_AD | Catalan | Andorra | ~79% |  |
| ko_KR | Korean | South Korea | ~78% |  |
| fr_FR | French | France | ~77% |  |
| ro_RO | Romanian | Romania | ~72% |  |
| ru_RU | Russian | Russia | ~67% |  |
| cs_CZ | Czech | Czech Republic | ~66% |  |
| fi_FI | Finnish | Finland | ~63% |  |
| de_DE | German | Germany | ~62% |  |
| it_IT | Italian | Italy | ~62% |  |
| uk_UA | Ukrainian | Ukraine | ~60% |  |
| pl_PL | Polish | Poland | ~57% |  |
| sv_SE | Swedish | Sweden | ~52% |  |
| no_NO | Norwegian | Norway | ~52% | Leif |
| hu_HU | Hungarian | Hungary | ~51% |  |
| li_NL | Limburgish | Netherlands | ~49% |  |
| nl_NL | Dutch | Netherlands | ~48% |  |
| sr_LATIN | Serbian | LATIN | ~48% |  |
| sr | Serbian |  | ~46% |  |
| ja_JP | Japanese | Japan | ~46% |  |
| lt_LT | Lithuanian | Lithuania | ~44% |  |
| da_DK | Danish | Denmark | ~43% |  |
| zh_TW | Chinese | Taiwan | ~40% |  |
| pt_PT | Portuguese | Portugal | ~40% |  |
| tr_TR | Turkish | Turkey | ~36% |  |
| sk_SK | Slovak | Slovakia | ~30% |  |
| th_TH | Thai | Thailand | ~29% |  |
| fy_NL | Frisian | Netherlands | ~27% |  |
| ka_GE | Georgian | Georgia | ~26% |  |
| in_ID | Indonesian | Indonesia | ~24% |  |
| bs_BA | Bosnian | Bosnia and Herzegovina | ~23% |  |
| el_GR | Greek | Greece | ~23% |  |
| sl_SI | Slovenian | Slovenia | ~21% |  |
| iw_IL | Hebrew | Israel | ~19% | AlmogK |
| oc | Occitan |  | ~16% |  |
| gl_ES | Gallegan | Spain | ~12% |  |
| ms_SG | Malay | Singapore | ~11% |  |
| ar_SA | Arabic | Saudi Arabia | ~9% |  |
| mk_MK | Macedonian | Macedonia | ~6% |  |
| hy_AM | Armenian | Armenia | ~4% |  |
| km_KH | Khmer | Cambodia | ~1% |  

Preferably, we'd love it if your native language wasn't English, however, anyone fluent in another language is very much appreciated.

## How to Translate

The Language files are "Java Properties" files, located in [core/src/com/biglybt/internat](core/src/com/biglybt/internat).  You can use your favorite translation tool, or, you can use one of the ways below:

### Crowdin

Visit our [BiglyBT Project Page on Crowdin](https://crowdin.com/projects/BiglyBT).  Crowdin is one of the best collaborative online localization tools, and has handy features such as suggesting translations from other projects with similar strings.


### BiglyBT Internationization Plugin

The advantage to using our plugin is that it will also try to pull in plugins that need translations.  Install the [Internationalize BiglyBT Plugin](https://plugins.biglybt.com/#i18nAZ) (If it doesn't prompt to launch BiglyBT, you can copy and paste the URL into the search bar of BiglyBT).

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
