# Offline field choices

The editor uses searchable pickers for collection/type, formats, publishers/labels, language, release year, condition, binding, game platform brand/platform, developer, region, edition, movie resolution, coin country/denomination/mint year/material. Titles, creators, subtitles, series, identifiers, counts, mint marks, borrowers and notes remain editable because they are open-ended or item-specific.

Every optional choice can be left blank. Custom opens a separate input step; only Use custom value changes the draft. Saved custom/fetched values remain verbatim and become reusable suggestions from the catalog. Collection/type are existing fixed choices, without arbitrary custom type creation. A collection is created from the main collection action.

## Dependencies

- Type selects format choices and category-specific fields. Changing type retains existing metadata, including hidden attributes.
- Game brand filters platforms. Legacy platform names infer a brand when recognized, without rewriting saved text. Changing a brand while a platform is set requires confirmation before removing that platform; cancel preserves both. An unrecognized existing platform remains editable through its picker. Custom brand then Custom platform supports other systems.
- Movie format selects DVD, Blu-ray or analog region suggestions. These are suggestions, not compatibility guarantees. Existing region values remain visible until explicitly replaced.
- Coin country selects common denomination suggestions, including some historical values. Custom supports other denominations, historical currencies and years before the preset range. Changing country does not remove an existing denomination.

The bundled lists are curated, not an exhaustive product database. Game platforms represent software families, rather than every hardware revision (for example Switch Lite and OLED find Nintendo Switch; PS5 Pro finds PlayStation 5). Xbox Series X and Series S are separate named choices. Custom supports hardware variants and missing systems. Platforms span Nintendo, Sony, Microsoft, Sega, Atari, SNK, NEC, 3DO, Philips, Magnavox, Bandai, Nokia, Commodore, Amstrad, Sinclair, Coleco, Mattel, GCE, Fairchild, Milton Bradley, computer/mobile systems and other systems.

Country and language choices use the device runtime’s offline ISO locale data with English names. Languages prioritize common choices and retain language-code search. Brand/platform search includes common abbreviations such as SNES, N64, GBA, PS5 and PSP; search ignores accents/case and matches each search word.

Catalog schema remains version 1. Platform brand is the optional attributes key `platformBrand`; platform remains `platform`. No migration drops or normalizes old values. Draft attributes use a saveable map so they survive activity recreation.

## Data and assets

Reference lists live in `core/FieldChoices.kt`; changes are reviewed with the app release. Current platform names were checked against manufacturer pages:
- https://www.nintendo.com/us/gaming-systems/
- https://www.playstation.com/en-us/ps5/
- https://www.xbox.com/en-US/consoles

32 brand/platform marks are bundled as VectorDrawables from Simple Icons 11.15.0; original paths are converted without geometry edits and use one dark fill for contrast. See `app/src/main/assets/brand-logos/NOTICE.md`, `LICENSE.md` and `DISCLAIMER.md` for provenance and notices. Assets are shown as identifiers, with no endorsement implied. Dedicated platform marks are used when available; other platforms use a brand mark or a generic icon. Unknown/custom choices use generic icons. No logo fetch, hosting, credential or network request is required.

To extend coverage, add a correctly sourced vector, preserve source and license notices, then update ChoiceMark. Do not substitute an unrelated system’s logo (Switch 2 intentionally uses the Nintendo brand mark).
