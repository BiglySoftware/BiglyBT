# Contributing to BiglyBT

:kissing_heart: Thank you for your interest in helping the BiglyBT community.  There are many ways you can help us, whether you are a normal user, tracker owner, blogger, Java guru, or rich person wanting to give away money.

If you are new to the Open Source Community and are looking for contributing 101, we highly recommend reading [How to Contribute to Open Source](https://opensource.guide/how-to-contribute/)

## Spread the Word

This is the most important contributing request from us.

BiglyBT is new and very unknown.  We had an excellent [article on TorrentFreak](https://torrentfreak.com/former-vuze-developers-launch-biglybt-a-new-open-source-torrent-client-170803/) which helped us get noticed with hardcore torrent fans, but that bump has now all but disappeared.

Mentioning BiglyBT on your favorite tracker site, blog, wiki, facebook, social media sites, and to friends would help us a lot.  Pointing out that we are an open source fork with backwards compatibility (and migration tools) with other same-source-code clients might help your friends decide to try us out.

If you don't see us on a comparison or "top 10" page of bittorrent clients, please contact the author and ask them to add us!  (Note: The Wikipedia bittorrent comparison page only allows new clients if they already have a main Wikipedia page)

Linking us with other bittorrent clients will probably help us with search engine rankings.  Even searching for BiglyBT with a few other key words, and clicking on the BiglyBT link might help (who knows! Search Engine rankings are mysterious beasts).  Other linking ideas:

* If you have an alternativeTo account, go to [alternativeTo](https://alternativeto.net), type in a bittorrent client name that's similar to BiglyBT (there should be at least one that's VERY similar to BiglyBT!), and add BiglyBT to its alternatives list.  You can also click the heart beside BiglyBT if you want ^^' ![](https://i.imgur.com/HaEnuD0.png)

If you are a tracker owner, we'd love to have a mention on your site as a bittorrent client to use.

## Translations

We have over 40 different translation files, but the vast majority of them are horribly incomplete.  If your native language isn't English, we'd love to have you contribute to the translations. Please see [TRANSLATE.md](TRANSLATE.md) for more information.

## Create Search Templates

Having a BiglyBT search template for your tracker or your favorite tracker helps users join your tracker community and increasing your swarm output.

However, search templates are a bit complex.  You'll need an understanding of regex and json.  There is a Template creation tool within BiglyBT (After you search, there's an "Add/Edit Templates" button.  The UI for it is a bit hard to use and a bit outdated, but it does (mostly) work.  In the future we'll have a wiki page on how to create one manually.

## Bug Reporting

If you find a reproducible bug, please file an [Issue](https://github.com/BiglySoftware/BiglyBT/issues).  If it's torrent related, we'd prefer if you reproduce the bug with a linux distro torrent so that we will be able to better reproduce it.

## Seed and Repair Swarms

The more you seed, the more BiglyBT will be listed in the peers list of other clients.  If you are the only person seeding, other users will take note as they impatiently wait for their full copy of the data.

When you are Swarm Merging, BiglyBT's client name changes for other peers to indicate you are using that feature.  If you search for other torrents that have  the same files you have already downloaded, but no seeders, and seed them, you will be the hero of the swarm.  Please note, that in order to swarm merge with an already completed torrent in your client, you need to turn on the "Try to complete all copies of the file" in Tools->Options->Files ![Image of Options page](http://i.imgur.com/fSRFw0g.png) 

Finding torrents with the same file is described on our wiki page [Swarm Merging](https://wiki.biglybt.com/w/Swarm_Merging)

You can also add a torrent with the same file, and link it to the existing file you already downloaded.  This skips swarm merging, and saves disk space.  But you will have to be careful when removing one of the torrent -- removing the 'torrent data' for one torrent will also remove it for the other torrent.

## Give Us Money :moneybag:

We'll just leave this here :smiley: -> [Support BiglyBT](https://www.biglybt.com/donation/donate.php) 

## Feature Voting

If you want to signal support for a feature that you think BiglyBT should have, visit our [Feature Voting](https://vote.biglybt.com/) page.

## Write Wiki Pages

In order to ensure the distinction that BiglyBT and Vuze are two separate, unaffiliated products, we will not directly link to wiki.vuze.com, even though the features and support documentation is virtually identical.  We are slowly writing our own wiki pages, but can always use your help.

## Write Code

See our [Coding Guidelines](CODING_GUIDELINES.md)

# Special Note for Tracker Owners

We've always given tracker owner's feature requests and complaints top priority.  For example, we were the first client to implement the min_request_interval after a tracker owner contacted us with a spec proposal.

If you have any issues with our client, please do not hesitate to contact us (info at biglybt dot com).  In the past, tracker owners preferred to keep their anonymity, so if that is a concern, you only need to mention you are "a tracker owner".  We can also hop on an IRC channel for longer conversations.



