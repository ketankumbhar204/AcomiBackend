# Discover information-completeness ranking

Customer discovery (`GET /api/v1/spaces/discover`) orders matching Places and Messes by how much enquiry-useful information they already have. The score is derived at query time. It is not stored, and it is not a quality, trust, or recommendation ranking.

## Order of operations

1. Apply the caller's filters (active/discoverable, type, location, search, rent, amenities).
2. Rank the matching rows by information completeness, highest first.
3. Break ties with the existing newest order: `createdAt` descending, then `id` descending.
4. Apply `page` and `size`.

A listing that does not match the filters is never promoted by a higher score.

## Score

Weights live only in `ListingInformationCompleteness`.

| Signal | Points |
| --- | --- |
| Usable Indian mobile contact | 4 |
| Usable address | 3 |
| Stored http(s) map URL | 2 |
| At least one amenity other than `FOOD_INCLUDED` | 1 |

Maximum is 10. Extra amenities do not add more than 1. Email-only contact, landlines, the admin placeholder mobile, blank values, and `NA` / `N/A` / `-` addresses do not score. Latitude and longitude do not count as a map URL.

The discover response still does not include owner phone numbers or the numeric score. `hasContact` remains the existing phone-or-email availability flag.
