# Cron Engine Guide

ChronoTask includes an internal, anti-drift, high-performance Cron evaluation engine supporting both Quartz and UNIX dialects.

## Dialects & Syntax

### Quartz Dialect (Default)
Supports 6 or 7 fields:
`second minute hour day-of-month month day-of-week [year]`

- `*` : Every value
- `/` : Step intervals (`*/15` seconds)
- `,` : Value lists (`1,15,30`)
- `-` : Range (`MON-FRI`)
- `?` : No specific value (used alone in day-of-month or day-of-week; forbidden in other fields and in lists, ranges, or steps)

Day-of-Week: `1 = Sunday, 2 = Monday, ..., 7 = Saturday`.

### UNIX Dialect
Supports 5 fields:
`minute hour day-of-month month day-of-week`

Day-of-Week: `0 = Sunday, 1 = Monday, ..., 6 = Saturday, 7 = Sunday`.

When neither day-of-month nor day-of-week starts with `*`, either day field may match (OR).
When either starts with `*`, including a step such as `*/2`, both fields must match (AND).
For example, `0 0 */2 * MON` runs only on Mondays with an odd day-of-month;
`0 0 1-31/2 * MON` runs on odd days-of-month or Mondays.

## Daylight Saving Time (DST)

ChronoTask handles DST spring gaps (skipped clock hours) and fall overlaps (repeated clock hours) gracefully across timezones by computing next valid UTC instants.
