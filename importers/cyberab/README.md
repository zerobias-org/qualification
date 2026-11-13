# cyberab importer

Fetches and extracts the CMMC, SCF and SCA ecosystem roles from cyberab.org.
Use the output to add or update packages under `package/`.

This directory is not part of any published package. A package publishes only
the files in its `package.json` `files` list.

## Run it

```bash
./scrape-cyberab.sh                                   # about 60s, 29 pages
python3 extract-cyberab.py .cyberab-mirror/cyberab.org > extract.json
```

Compare `extract.json` with the packages under `package/` and add what is new.
`.cyberab-mirror/` and `extract.json` are working files. Do not commit them.

The extractor does not decide what becomes a qualification, its code, or its
links. A person does that. See [`../../AGENTS.md`](../../AGENTS.md).

## What it covers

cyberab.org is the source for the CMMC, SCF and SCA ecosystem qualifications:
CCP, CCA, LCCA, CCI, RP, RPA, C3PAO, RPO, ATP, APP, the SCF practitioner,
architect and assessor track and its organizational baselines, and the SCA CODE
levels. Other issuers (CompTIA, GIAC, ISC2, ISACA, IAPP, OffSec, CREST, ISO lead
auditor) were curated by hand from issuer sites. This importer does not
reproduce them.

## Known limits

**It fetches a fixed page list, not a mirror.** cyberab.org pulls about 1.5MB of
CSS and a long JS tail per page. A recursive mirror took hours. The script
fetches only the pages in the `PAGES` map in `extract-cyberab.py`. That map is
the one source of truth. A moved page prints `MISS <code>`. Fix the path in the
map when you see one.

**The `*-Registration` pages are client-rendered.** 11 of the 12 return only
"Skip to main content Go to Top", because JavaScript injects their content. An
empty `body_excerpt` there does not mean the role was withdrawn. The role names
and requirement grids come from the `Ecosystem-Roles` pages, which the server
renders.

## Facts from the source (captured 2026-08-20)

- **ISACA is the Authorized CAICO.** The CMMC pages say: *"ISACA is now the
  Authorized CAICO for the CMMC program. If you are interested in the CCP, CCA,
  LCCA or CCI credentials, please visit www.isaca.org/cmmc"*. ISACA issues those
  four. Cyber AB stays the accreditation body and the issuer for RP, RPA, RPO,
  C3PAO, ATP and APP.
- **The 8140.3-612 grid is a prerequisite list.** The Intermediate and Advanced
  table on the Assessing-and-Certification page lists the certifications that
  count toward CCP and CCA eligibility. They go in `prerequisites`, not in
  packages of their own.
- **Renames:** ATP was LTP, APP was LPP, CompTIA SecurityX was CASP+, ISC2 CGRC
  was CAP. Use `supersedes`.
- **SCF organizational baselines are certifications.** The SCF Certifications
  page calls them *"organizational certification baselines"*. An SCF 3PAO
  certifies an organization against, for example, the NIST CSF 2.0 baseline.
- **SCA CODE is organizational:** *"Certified Organization for Development
  Excellence (CODE) framework includes three progressive levels of
  certification"* for organizations.
