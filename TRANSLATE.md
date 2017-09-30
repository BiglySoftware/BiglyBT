# Translating BiglyBT

Thank you for your interesting in helping out BiglyBT.  We really need help with translating BiglyBT to as many languages possible.  Translating BiglyBT is no small task.  We have over 4000 translation strings, and many of the 40 language we bundle are very incomplete.  Here's a breakdown of the completion level of each language in v1.0.2.0:

| Locale | Language | Country | % Translated | Active Translator(s) |
|:---|:---|:---|---:|:---|
| pt_BR | Portuguese | Brazil | ~99% | [Havokdan](https://github.com/Havokdan) |
| eu | Basque |  | ~98% | |
| zh_CN | Chinese | China | ~90% | |
| bg_BG | Bulgarian | Bulgaria | ~84% | andreshko |
| fr_FR | French | France | ~84% | |
| es_ES | Spanish | Spain | ~81% | Valtiel |
| ca_AD | Catalan | Andorra | ~80% | |
| ko_KR | Korean | South Korea | ~79% | |
| ro_RO | Romanian | Romania | ~73% | |
| ru_RU | Russian | Russia | ~68% | |
| cs_CZ | Czech | Czech Republic | ~68% | |
| fi_FI | Finnish | Finland | ~64% | |
| de_DE | German | Germany | ~63% | |
| it_IT | Italian | Italy | ~63% | |
| uk_UA | Ukrainian | Ukraine | ~61% | |
| pl_PL | Polish | Poland | ~58% | |
| sv_SE | Swedish | Sweden | ~53% | |
| no_NO | Norwegian | Norway | ~53% | Leif |
| hu_HU | Hungarian | Hungary | ~52% | |
| li_NL | Limburgish | Netherlands | ~50% | |
| nl_NL | Dutch | Netherlands | ~49% | |
| sr_LATIN | Serbian | LATIN | ~49% | |
| sr | Serbian |  | ~47% | |
| ja_JP | Japanese | Japan | ~47% | |
| lt_LT | Lithuanian | Lithuania | ~45% | |
| da_DK | Danish | Denmark | ~44% | |
| zh_TW | Chinese | Taiwan | ~41% | |
| pt_PT | Portuguese | Portugal | ~40% | |
| tr_TR | Turkish | Turkey | ~36% | |
| sk_SK | Slovak | Slovakia | ~31% | |
| th_TH | Thai | Thailand | ~29% | |
| fy_NL | Frisian | Netherlands | ~28% | |
| ka_GE | Georgian | Georgia | ~27% | |
| bs_BA | Bosnian | Bosnia and Herzegovina | ~24% | |
| el_GR | Greek | Greece | ~24% | |
| in_ID | Indonesian | Indonesia | ~24% | |
| sl_SI | Slovenian | Slovenia | ~22% | |
| iw_IL | Hebrew | Israel | ~19% | |
| oc | Occitan |  | ~16% | |
| gl_ES | Gallegan | Spain | ~12% | |
| ms_SG | Malay | Singapore | ~11% | |
| ar_SA | Arabic | Saudi Arabia | ~9% | |
| mk_MK | Macedonian | Macedonia | ~6% | |
| hy_AM | Armenian | Armenia | ~4% | |
| km_KH | Khmer | Cambodia | ~1% | |


Preferably, we'd love it if your native language wasn't English, however, anyone fluent in another language is very much appreciated.

## How to Translate

The Language files are "Java Properties" files, located in [core/src/com/biglybt/internat](core/src/com/biglybt/internat).  You can use your favorite translation tool, or, you can use one of the two ways below:

### BiglyBT Internationization Plugin

This is the recommended way, as it will also try to pull in plugins that need translations.  Install the [Internationalize BiglyBT Plugin](https://plugins.biglybt.com/#i18nAZ) (If it doesn't prompt to launch BiglyBT, you can copy and paste the URL into the search bar of BiglyBT).

### IntelliJ

IntelliJ Idea IDE has an ok 2-panel translation tool, and there are many tutorials out there on how to setup IntelliJ with a GitHub project.  Once setup, you can open MessagesBundle.properties, and switch to the "Resource Bundle" tab.  Unfortunately it doesn't filter on just one language, so it can be a bit annoying to use.

## Submitting Translations

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
