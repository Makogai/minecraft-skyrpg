#!/usr/bin/env python3
"""Publishes the wiki website to GitHub Pages (https://makogai.github.io/minecraft-skyrpg/).

    python tools/wiki/publish_pages.py               # build the wiki, then push it to the gh-pages branch
    python tools/wiki/publish_pages.py --skip-build  # push what's already in dist/wiki
    python tools/wiki/publish_pages.py --no-push     # commit to gh-pages locally, don't push

The site is built here, not by a GitHub action, because the item icons come from the Minecraft client jar in the local
Gradle cache. The site is the whole dist/wiki tree (index.html, wiki.css, wiki.js, data/, img/ and .nojekyll); the gh-pages
branch holds only that, and lives in a git worktree at .gh-pages/ (gitignored). Files that no longer exist in dist/wiki are
removed from the branch, except the top-level pack/ folder (the packwiz pack, see tools/publish_pack.py). Pages must be set to "Deploy from a branch: gh-pages / (root)" in the repo settings once.
"""
import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
WORKTREE = ROOT / ".gh-pages"
BRANCH = "gh-pages"
SITE = ROOT / "dist" / "wiki"
KEEP = {"pack"}    # top-level folders owned by tools/publish_pack.py: never touched here


def git(*args, cwd=ROOT, check=True):
    result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if check and result.returncode != 0:
        sys.exit(f"git {' '.join(args)} failed:\n{result.stderr.strip()}")
    return result


def ensure_worktree():
    """Creates the gh-pages worktree at .gh-pages/ if it is missing (also used by tools/publish_pack.py)."""
    if not (WORKTREE / ".git").exists():
        has_local = git("rev-parse", "--verify", "--quiet", f"refs/heads/{BRANCH}", check=False).returncode == 0
        has_remote = git("rev-parse", "--verify", "--quiet", f"refs/remotes/origin/{BRANCH}", check=False).returncode == 0
        if has_local:
            git("worktree", "add", str(WORKTREE), BRANCH)
        elif has_remote:
            git("worktree", "add", "-b", BRANCH, str(WORKTREE), f"origin/{BRANCH}")
        else:
            git("worktree", "add", "--orphan", "-b", BRANCH, str(WORKTREE))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--no-push", action="store_true")
    args = parser.parse_args()
    if not args.skip_build:
        if subprocess.run([sys.executable, str(ROOT / "tools" / "wiki" / "build.py")], cwd=ROOT).returncode != 0:
            sys.exit("The wiki build failed.")
    if not (SITE / "index.html").exists():
        sys.exit(f"No site at {SITE}; run tools/wiki/build.py first.")

    ensure_worktree()

    # mirror dist/wiki into the worktree: remove what is gone, copy what is new or changed
    for path in sorted(WORKTREE.rglob("*"), key=lambda p: len(p.parts), reverse=True):
        if ".git" in path.relative_to(WORKTREE).parts[:1] or path.name == ".git":
            continue
        rel = path.relative_to(WORKTREE)
        if rel.parts[0] in KEEP:
            continue
        if path.is_file() and not (SITE / rel).is_file():
            path.unlink()
        elif path.is_dir() and not (SITE / rel).is_dir():
            shutil.rmtree(path, ignore_errors=True)
    for path in SITE.rglob("*"):
        rel = path.relative_to(SITE)
        if rel.parts[0] in KEEP:
            continue
        if rel.name.startswith("_") and rel.parent == Path("."):
            continue    # scratch files (test pages) never go to the site
        target = WORKTREE / rel
        if path.is_dir():
            target.mkdir(parents=True, exist_ok=True)
        elif not target.exists() or target.read_bytes() != path.read_bytes():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
    (WORKTREE / ".nojekyll").write_text("", encoding="utf-8")
    git("add", "--all", cwd=WORKTREE)
    if git("diff", "--cached", "--quiet", cwd=WORKTREE, check=False).returncode == 0:
        print("The published wiki is already up to date.")
    else:
        version = git("rev-parse", "--short", "HEAD", check=False).stdout.strip() or "uncommitted"
        git("commit", "-m", f"Wiki build from {version}", cwd=WORKTREE)
        print(f"Committed the wiki to {BRANCH}.")
    if args.no_push:
        return
    result = git("push", "origin", BRANCH, cwd=WORKTREE, check=False)
    if result.returncode != 0:
        sys.exit(f"Push failed:\n{result.stderr.strip()}")
    print(f"Pushed {BRANCH}. Live in a minute at https://makogai.github.io/minecraft-skyrpg/")


if __name__ == "__main__":
    main()
