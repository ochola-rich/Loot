# AGENTS.md

Instructions for any AI coding agent working in this repository.
Read this file completely before making changes. These rules take precedence over
your default behavior. If a rule here conflicts with a user instruction in the
session, the user wins — but say out loud which rule you are setting aside.

---

## 0. Project context

> Fill this in per project. Everything below section 0 is project-agnostic.

- **Project:** `TODO`
- **Stack / language:** `TODO`
- **Package manager:** `TODO`
- **Install:** `TODO`
- **Run tests:** `TODO`
- **Lint / format:** `TODO`
- **Type check:** `TODO`
- **Build:** `TODO`
- **Default branch:** `main`
- **Issue tracker prefix:** `TODO` (e.g. `PROJ-`)

---

## 1. Prime directive

The commit history is a permanent engineering artifact. Someone running
`git blame` or `git log` two years from now must be able to understand *why* a
line exists, without asking anyone.

Write history the way a careful senior engineer would: small, intentional,
explained, and reversible. Nothing in the history should read as machine output —
no attribution banners, no template phrasing, no file-by-file narration of the
diff.

### Commit as you go. You do not need permission.

Committing is part of doing the work, not a separate step someone approves.

- **If you changed anything, commit it.** A deleted line, a renamed variable, a
  fixed typo in a comment, a tightened import — it gets a commit. There is no
  minimum size.
- **Never wait to be told to commit.** Don't ask "should I commit this?", don't
  announce "let me know when you want this committed", and don't hold finished
  work in the working tree hoping for a bigger batch.
- **Never end a task with a dirty tree.** When you hand control back, `git status`
  on the files you touched should be clean. Anything you wrote is either
  committed or explicitly explained to the human as unfinished.
- **Commit the moment a change is coherent**, not when the whole feature is done.
  Working from a plan with six steps means roughly six commits, landed as you
  finish each one — not one commit at the end.
- The only things you don't commit are the things in section 8.

This applies to cleanup too. If you delete dead code, drop an unused dependency,
or remove a stale comment while working on something else, that's its own commit
with its own message — not an unexplained hitchhiker inside a feature diff.

---

## 2. Orient before you touch anything

Run this first, every session:

```bash
git status
git branch --show-current
git log --oneline -30
git diff
```

Then:

- **Uncommitted changes are the human's work.** Never discard, revert, stash-drop,
  or overwrite them. If they block you, stop and ask. If you must stash, use a
  named stash (`git stash push -m "agent: parking WIP before X"`) and say so.
- **The repo's existing conventions outrank this document.** If the last 30
  commits use Conventional Commits, use them. If they don't, do not introduce
  them unilaterally. Match subject casing, tense, ticket-reference placement, and
  scope naming that already exist.
- Read `CONTRIBUTING.md`, `.gitmessage`, `commitlint.config.*`, `.pre-commit-config.yaml`,
  and any hooks in `.githooks/` before your first commit.

---

## 3. Commit message format

```
<subject — imperative, ≤50 chars preferred, 72 hard max, no trailing period>
<blank line>
<body — wrapped at 72 columns, explains WHY and the tradeoffs>
<blank line>
<footers — Refs:, Fixes:, BREAKING CHANGE:, Co-authored-by: (humans only)>
```

**Subject line**

- Imperative mood. It must complete the sentence: *"If applied, this commit will
  ___."* → `Add retry to token refresh`, not `Added`, `Adds`, or `Adding`.
- Capitalize the first word. No period at the end.
- Say what changed at the level of intent, not the level of files.
- If the repo uses Conventional Commits, prefix accordingly:
  `feat(auth): expire refresh tokens after 30 days`

**Body**

Include one whenever the change isn't self-evident. Cover:

- The problem or the trigger (bug report, timeout in prod, review feedback).
- Why *this* approach, and what else was considered and rejected.
- Anything surprising: a workaround, a known limitation, a follow-up needed.
- Migration or operational impact.

Skip the body only for genuinely trivial commits (typo fix, dependency bump,
version tag). Never pad a body just to have one.

**Footers**

```
Fixes: #482
Refs: PROJ-1173
BREAKING CHANGE: `parseConfig()` now throws instead of returning null.
```

---

## 4. Banned in commit messages

Absolutely never:

- **Any AI attribution or tooling reference.** No `Co-Authored-By:` an assistant,
  no "Generated with", no "🤖", no mention of prompts, sessions, models, or that
  a tool wrote the change. The commit is authored under the repo's configured
  identity, full stop.
- **Emoji**, unless the repo already uses gitmoji consistently.
- **Filler subjects:** `wip`, `fix`, `update`, `changes`, `misc`, `cleanup`,
  `final fix`, `fix fix`, `asdf`, `.`
- **Diff narration:** listing every touched file, or "Updated `a.ts`. Updated
  `b.ts`. Updated `c.ts`." The diff already says that.
- **Marketing adjectives:** comprehensive, robust, seamless, production-ready,
  enterprise-grade, blazing-fast, world-class.
- **Self-congratulation:** "massive refactor", "huge improvement", "significantly
  better".
- **Bullet-point walls** with a bullet per file. Prose paragraphs, or at most 2–4
  bullets covering distinct decisions.
- **Uncertainty:** "hopefully fixes", "should work now", "attempt at". If you
  aren't sure it works, don't commit it — say so instead.

---

## 5. Examples

| Bad | Good |
|---|---|
| `Updated files` | `Drop unused legacy CSV importer` |
| `fix bug` | `Handle empty payload in webhook receiver` |
| `Added comprehensive error handling to the API layer 🚀` | `Return 422 instead of 500 on malformed filter params` |
| `refactor: various improvements to auth module` | `refactor(auth): extract token validation from middleware` |
| `WIP part 3` | `Add failing test for expired session reuse` |

A full example:

```
Cache locale bundles between renders

Every render re-read the JSON bundle off disk, which showed up as ~40ms
of blocking I/O per request under load. Bundles are immutable after
build, so a process-level Map is safe here.

Chose a plain Map over an LRU because the bundle set is bounded by the
supported-locale list (currently 9) and never grows at runtime.

Refs: PROJ-1173
```

---

## 6. Atomicity — one logical change per commit

A commit is correct when it can be reverted on its own without breaking anything
else, and the test suite passes at that commit.

Split these apart, always:

- Formatting / whitespace vs. behavior change
- File moves & renames vs. edits to those files (`git mv` first, commit, then edit)
- Dependency upgrades vs. code that adapts to them
- Refactors that preserve behavior vs. changes that alter behavior
- Test additions for existing behavior vs. new features
- Generated files / lockfiles vs. hand-written source

**Stage with intent:**

```bash
git add -p                 # preferred — review each hunk
git add src/auth/token.ts  # or explicit paths
```

Never `git add -A` or `git add .` reflexively — it sweeps up scratch files,
`.env`, editor state, and unrelated work.

**Always review before committing:**

```bash
git diff --staged
```

If the staged diff contains something you can't explain in the message you're
about to write, unstage it.

---

## 7. Commit rhythm should look human

Frequent is good. Twenty-five commits in a session is fine and normal — that's
what an engineer's day actually looks like. The thing to avoid is not *many*
commits, it's *incoherent* ones.

- **Small is fine. One line is fine.** `Remove unused lodash import` is a real,
  legitimate commit. Don't sit on it.
- A one-line change gets a one-line commit: subject only, no body. Reserve bodies
  for changes where the reasoning isn't obvious from the diff.
- **Don't dump** 2,000 lines across 40 files into a single "implement feature"
  commit. A typical commit is roughly 1–400 changed lines; larger needs a reason
  (vendored code, lockfile, generated client) and that reason goes in the body.
- **Don't split artificially either.** Commit per *finished thought*, not per
  file and not per keystroke. If one idea touches four files, that's one commit
  across four files — not four commits.
- Never commit a half-edited state: no broken syntax, no function renamed in one
  place but not its callers, no test left mid-rewrite. Each commit stands on its
  own.
- Vary your phrasing. Ten commits that all open with the same template verb read
  as generated.

---

## 8. Never commit

- Secrets: `.env`, API keys, tokens, private keys, credentials, connection
  strings. **If a secret is ever staged or already committed, stop immediately,
  do not push, and tell the human — rotation is required.**
- Build output, `dist/`, `build/`, `node_modules/`, `target/`, `__pycache__/`,
  coverage reports, `.DS_Store`, IDE folders. Add them to `.gitignore` instead.
- Commented-out code, leftover `console.log` / `print` / `dbg!`, or debugging
  scaffolding.
- Large binaries without Git LFS.
- Unrelated auto-format churn from your editor touching files you didn't change.
- Code that doesn't compile or that fails tests, on any shared branch.
- Personal config: local paths, machine-specific settings, your own git identity.

---

## 9. Pre-commit gate

Committing often shouldn't mean committing junk. Two tiers:

**Before every commit** (fast — seconds):

1. Formatter
2. Linter
3. Type checker
4. The tests covering what you touched

**Before pushing, opening a PR, or handing back** (slow — the real gate):

5. Full test suite
6. Build

A commit is never an excuse to skip tier one. If tier one is slow enough that it
discourages committing, say so — that's a repo problem worth reporting, not a
reason to batch up changes.

If something fails and it's **unrelated** to your change: do not silently fix it
and fold it into your commit. Either commit the fix separately with its own
message, or report it and leave it alone.

**Never bypass hooks.** `--no-verify` is forbidden. If a hook fails, fix the
cause. If a hook is genuinely broken, stop and tell the human.

---

## 10. Branching

- **Never commit directly to `main`, `master`, `develop`, or any release branch**
  unless explicitly told to in this session.
- Create a branch before work starts:

  ```
  feat/oauth-token-refresh
  fix/PROJ-1173-empty-webhook-payload
  chore/bump-pnpm-10
  docs/api-auth-examples
  refactor/extract-rate-limiter
  test/session-reuse-coverage
  ```

  Lowercase, kebab-case, one scope, short. Include the ticket ID if the repo
  does.
- One branch = one reviewable unit of work. If scope grows, branch again.
- Rebase onto the latest default branch before opening or updating a PR.

---

## 11. History safety — the hard rules

**Allowed** (your own local, unpushed commits only):
`git commit --amend`, interactive rebase, squash, reorder, reword.

**Forbidden without explicit, in-session human approval:**

- Rewriting any commit that has been pushed or that exists on a shared branch.
- `git push --force` — if force is approved, use `--force-with-lease` and only on
  your own feature branch, never on the default branch.
- `git reset --hard`, `git clean -fdx`, `git checkout -- .`, `git restore .`,
  branch deletion, tag deletion, `git filter-branch` / `filter-repo`.
- Changing `git config` values, especially `user.name` and `user.email`. Commit
  under whatever identity the repo/environment already has.
- Amending someone else's commit.
- Rewriting the default branch under any circumstance.

**To undo something already shared, use `git revert`.** It's honest, it preserves
history, and it's reviewable:

```bash
git revert <sha>            # single commit
git revert -m 1 <merge-sha> # merge commit
```

**Pushing** is looser than committing, but not free:

- Push your own feature branch freely, as often as you like, once the full gate
  in section 9 passes. No need to ask.
- **Never push to `main`, `master`, `develop`, or a release branch** without
  explicit approval in this session.
- Never force-push a branch someone else may have pulled.

Committing, by contrast, is never gated on approval — see section 1.

---

## 12. Merges and conflicts

- Integrate with `git rebase` for your own unmerged feature branch; use the
  repo's stated policy (merge commit vs. squash vs. rebase) for landing PRs.
- Resolve conflicts by understanding both sides. Never resolve by blindly taking
  `--ours` or `--theirs`.
- Never leave `<<<<<<<`, `=======`, `>>>>>>>` markers anywhere, including in
  tests, snapshots, and lockfiles.
- After resolving, re-run the full pre-commit gate before continuing.
- If the conflict touches logic whose intent you can't determine, **stop and
  ask.** Do not guess in someone else's domain.

---

## 13. Pull requests

- Title follows the same rules as a commit subject.
- Description covers: what changed, why, how it was tested, risk/rollback, and
  linked issues. Screenshots or output for anything user-visible.
- Keep PRs under ~400 changed lines where possible. Split larger work into
  stacked PRs.
- Open as **draft** if incomplete.
- Same ban list applies: no AI attribution, no marketing language, no emoji-laden
  headers.
- Respond to review comments with commits, not force-pushed rewrites, until the
  review is resolved.

---

## 14. Tags, releases, changelogs

- Semantic versioning: `MAJOR.MINOR.PATCH`, tags as `v1.4.2`.
- Only tag or release when explicitly asked.
- If the repo keeps a `CHANGELOG.md`, update it in the same commit as the change,
  in the repo's existing format. Write entries for humans reading release notes,
  not for machines parsing them.

---

## 15. Stop and ask when

- A change would touch secrets, auth, payments, migrations, or data deletion.
- History rewriting or force-pushing seems necessary.
- Uncommitted human work is in the way.
- The task requires deleting files or dropping a database column/table.
- Tests fail for reasons you don't understand.
- A conflict sits in code whose intent isn't clear.
- Scope has grown well past what was asked.
- You're about to disable a lint rule, a test, or a hook to make something pass.

Asking costs one message. A rewritten shared branch costs an afternoon of
someone else's time.

**None of these are permission to stop committing.** Every gate above is about
*what you change*, never about *whether you record it*. Work you were authorized
to do gets committed the moment it's coherent — no confirmation, no waiting.

---

## 16. Final checklist before every commit

- [ ] `git diff --staged` reviewed line by line, nothing unexplained
- [ ] One logical change only
- [ ] No secrets, build artifacts, debug output, or unrelated churn
- [ ] Format, lint, types, tests, build all pass
- [ ] Subject: imperative, ≤50–72 chars, capitalized, no period
- [ ] Body explains *why* (or the change is genuinely trivial)
- [ ] No AI attribution, no emoji, no marketing words, no filler
- [ ] Style matches the surrounding history
- [ ] Ticket/issue referenced if the repo does that
- [ ] Hooks not bypassed
- [ ] Committed without being asked, as soon as the change was coherent
- [ ] Nothing you changed is still sitting uncommitted in the working tree
