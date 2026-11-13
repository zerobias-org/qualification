# Qualification monorepo

ZeroBias **qualification** artifacts. A qualification is evidence that a party
holds an assessed competency, conformity or standing: a certification,
accreditation, license, degree, clearance or registration. Qualifications link
issuers (vendors), the standards they attest to, and the catalog work roles
they qualify a holder for.

Each `package/<issuer>/<code>/` directory is one publishable qualification, for
example `isc2/cissp`.

## The schema

Packages follow the `Qualification` interface in the ZeroBias base schema
(`auditlogic/schema#232`, a port of `zerobias-org/schema#80`):

| Field | Kind | Values |
|---|---|---|
| `id`, `name`, `description`, `aliases`, `url` | from `Object` | |
| `code` | string | namespaced, for example `ISC2.CISSP` |
| `type` | enum | `certification`, `accreditation`, `authorization`, `registration`, `license`, `degree`, `clearance` |
| `scope` | enum | `individual`, `organizational` |
| `status` | enum | `active`, `retired`, `superseded` |
| `vendors` | link, many | `Vendor` (the issuer) |
| `standards` | link, many | `Standard` (a framework is a standard) |
| `qualifiesForRoles` | link, many | catalog `Role` |

`type` says what the qualification is. `scope` says who holds it. The two vary
independently.

| `type` | Rule |
|---|---|
| `certification` | The holder was assessed against a standard. |
| `accreditation` | The holder is authorized to assess or certify others. |
| `authorization` | The holder is approved to deliver something (training, a platform, content), not to assess. |
| `registration` | The holder is listed on a registry after training or agreement, with no independent assessment. |

The packages also carry two fields that the interface does not have yet:
`prerequisites` (qualification codes, any one of which satisfies the
requirement) and `supersedes` (the code of the qualification this one replaced).

## Package layout

```
package/<issuer>/<code>/
  index.yml          # the qualification
  package.json       # identity, dependencies, zerobias block
  .npmrc             # byte-identical copy of the repo-root .npmrc
  build.gradle.kts   # plugins { id("zb.content") }
  logo.*             # optional
```

### Naming

| | Value | Example |
|---|---|---|
| dir | `package/<issuer>/<code>/` | `package/isc2/cissp/` |
| npm `name` | `@zerobias-org/qualification-<issuer>-<code>` | `@zerobias-org/qualification-isc2-cissp` |
| `zerobias.package` | `<issuer>.<code>.qualification` | `isc2.cissp.qualification` |
| `zerobias.import-artifact` | `qualification` | |
| `index.yml` `id` | UUIDv5(nil namespace, `code`) | `93c63eb6-f9cf-5719-ba43-e650f5da12a0` |

`<issuer>` is the issuer's vendor code from `zerobias-org/vendor`. `<code>` is
lowercase `[a-z0-9_]`.

### Links

Links in `index.yml` name the target by its code:

- `vendors`: vendor codes, for example `isc2`
- `standards`: standard codes, for example `nist_csf_v2_scf`
- `qualifiesForRoles`: catalog role codes
- `prerequisites`, `supersedes`: qualification codes

Each linked vendor, standard and in-repo qualification is also a `dependencies`
entry in `package.json`, so it loads first.

## Samples

| Package | Code | Shows |
|---|---|---|
| `isc2/cissp` | `ISC2.CISSP` | the basic case |
| `isc2/cgrc` | `ISC2.CGRC` | `supersedes` (CGRC was CAP) |
| `comptia/security_plus` | `CompTIA.Security+` | a code that needs a different directory name |
| `isaca/ccp` | `CMMC.CCP` | `prerequisites`, and an issuer that differs from the code prefix |
| `cyberab/rp` | `CYBERAB.RP` | `registration` |
| `cyberab/c3pao` | `CYBERAB.C3PAO` | `accreditation`, organizational scope |
| `cyberab/apo` | `SCF.APO` | `authorization`, organizational scope |
| `scf/nist_csf_2_0` | `SCF.NIST-CSF-2.0` | a `standards` link (NIST CSF 2.0) |

## Sources

| Source | Covers | Importer |
|---|---|---|
| cyberab.org | CMMC, SCF and SCA ecosystem roles | [`importers/cyberab/`](importers/cyberab/) |
| DoD 8140 qualification matrix (`@zerobias-org/framework-dod-dcwf-v2_1`) | which certifications qualify for which DCWF work roles | none yet |
| Issuer websites | everything else | none (curated by hand) |

Each source issuer is a vendor. Importers live under `importers/` and are never
published.

## Creating a new qualification

```bash
sh scripts/createNewQualification.sh <issuer> <code> <qualificationCode> "<name>"
sh scripts/createNewQualification.sh isc2 cissp ISC2.CISSP "Certified Information Systems Security Professional"
```

Then fill `index.yml`, add dependencies to `package.json`, and run
`zbb :<issuer>:<code>:gate`. See [`AGENTS.md`](AGENTS.md) for the full
procedure.

## Build and validate

Use [`zbb`](https://github.com/zerobias-org/util/tree/main/packages/zbb) for
every build command. Do not run `./gradlew` directly: zbb selects Java 21, and
Gradle 8.10.2 fails on Java 25 with only `* What went wrong: 25.0.2`.

```bash
npm i -g @zerobias-org/zbb@latest @zerobias-com/platform-dataloader@latest

unset ZB_SLOT                                # a content repo has no slot or stack
zbb :<issuer>:<code>:validateContent         # file shape only
zbb :<issuer>:<code>:gate                    # full gate (writes gate-stamp.json)
zbb validateUniqueIds                        # repo-wide id check
```

Use the task-path form (`:<issuer>:<code>:gate`). The bare `zbb gate` needs a
loaded slot.

The gate loads the package into the dataloader service with `ZB_TOKEN`. If
`ZB_TOKEN` is empty, that step is skipped and the gate still writes a stamp.
Check that `testDataloader` is `"passed"` in `gate-stamp.json`, then commit the
stamp with the package.

The repo validator checks: the dir, npm name and `zerobias.package` agree; the
`id` is UUIDv5(nil, `code`); the directory's issuer is in `vendors`; and no two
packages share an `id`. The dataloader checks everything else.

## Publishing

`.github/workflows/publish.yml` invokes
`zerobias-org/devops/.github/workflows/zbb-publish-reusable.yml@main` on push to
`main`, `qa`, `dev` and `uat`.

## Commit format

[Conventional Commits](https://www.conventionalcommits.org/), commitlint-enforced.
Scope: `qualification-<issuer>-<code>`.

## Prerequisites: GitHub token with `read:packages`

Required before any `zbb` command. The `zb.*` gradle plugins resolve
from GitHub Packages Maven, which refuses anonymous reads. Being logged in to
`gh` is not enough. The scope is separate:

```bash
gh auth status 2>&1 | grep -q 'read:packages' && echo OK || echo 'MISSING read:packages'
gh auth refresh -s read:packages && export GITHUB_TOKEN=$(gh auth token)
```

Without it the build fails on its first request with a 401 or
`Plugin [id: 'zb.workspace'] was not found`.
