"""Publish Lava Genesis to Modrinth and/or CurseForge.

    python tools/publish.py modrinth [--submit]      # create the project (draft), upload the version + gallery;
                                                     # --submit also sends it to Modrinth review
    python tools/publish.py curseforge <project_id>  # upload the jar to an existing CurseForge project

Tokens come from the environment: MODRINTH_TOKEN, CURSEFORGE_TOKEN. Nothing is printed of them.
Results (project/version ids) are saved to tools/publish_state.json so a rerun does not create a second project.
"""
import json
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "dist" / "lavagenesis-1.0.0.jar"
ICON = ROOT / "src" / "main" / "resources" / "assets" / "lavagenesis" / "icon.png"
BODY = ROOT / "media" / "description.md"
STATE = ROOT / "tools" / "publish_state.json"
VERSION = "1.0.0"
SOURCE = "https://github.com/davitotty/lava-genesis"
SUMMARY = ("Be born as a single spark of lava in an undersea volcano and grow into a Hawaiian-style "
           "archipelago and a Great Volcano.")
CHANGELOG = "First release for Minecraft 26.3 (Fabric)."
GALLERY = [
    ("ember_chamber.png", "Born in the magma chamber", True),
    ("seamount.png", "Seamount: a lava golem in the deep", False),
    ("archipelago.png", "Archipelago", False),
    ("great_eruption.png", "The Great Eruption", False),
]
UA = "lavagenesis-publisher/1.0"


def load_state():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def save_state(state):
    STATE.write_text(json.dumps(state, indent=2) + "\n")


def multipart(fields, files):
    boundary = uuid.uuid4().hex
    out = bytearray()
    for name, value in fields.items():
        out += f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n".encode()
        out += b"Content-Type: application/json\r\n\r\n" + value.encode() + b"\r\n"
    for name, (filename, data, ctype) in files.items():
        out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{filename}\"\r\n"
                f"Content-Type: {ctype}\r\n\r\n").encode() + data + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


def request(method, url, headers, body=None, ctype=None):
    h = dict(headers, **{"User-Agent": UA})
    if ctype:
        h["Content-Type"] = ctype
    req = urllib.request.Request(url, data=body, headers=h, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read().decode()
            return json.loads(text) if text.strip() else None
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {url} failed: HTTP {e.code}: {e.read().decode()[:800]}")


def modrinth(submit):
    import os
    token = os.environ["MODRINTH_TOKEN"]
    auth = {"Authorization": token}
    api = "https://api.modrinth.com"
    state = load_state()

    if "modrinth_project" not in state:
        data = {
            "slug": "lava-genesis", "title": "Lava Genesis", "description": SUMMARY,
            "categories": ["adventure", "game-mechanics", "worldgen"],
            "client_side": "required", "server_side": "required",
            "body": BODY.read_text(encoding="utf-8"), "license_id": "MIT", "project_type": "mod",
            "source_url": SOURCE, "issues_url": SOURCE + "/issues", "is_draft": True, "initial_versions": [],
        }
        body, ctype = multipart({"data": json.dumps(data)}, {"icon": ("icon.png", ICON.read_bytes(), "image/png")})
        project = request("POST", f"{api}/v2/project", auth, body, ctype)
        state["modrinth_project"] = project["id"]
        save_state(state)
        print("created Modrinth project", project["id"], "(draft)")
    pid = state["modrinth_project"]

    if "modrinth_version" not in state:
        data = {
            "name": f"Lava Genesis {VERSION}", "version_number": VERSION, "changelog": CHANGELOG,
            "dependencies": [{"project_id": "P7dR8mSH", "dependency_type": "required"}],
            "game_versions": ["26.3"], "version_type": "release", "loaders": ["fabric"], "featured": True,
            "project_id": pid, "file_parts": ["file"], "primary_file": "file",
        }
        body, ctype = multipart({"data": json.dumps(data)},
                                {"file": (JAR.name, JAR.read_bytes(), "application/java-archive")})
        version = request("POST", f"{api}/v2/version", auth, body, ctype)
        state["modrinth_version"] = version["id"]
        save_state(state)
        print("uploaded version", version["id"])
    vid = state["modrinth_version"]
    # Modrinth keeps the client/server environment per version (API v3); review is refused without it.
    request("PATCH", f"{api}/v3/version/{vid}", auth, json.dumps({"environment": "client_and_server"}).encode(),
            "application/json")

    done = set(state.get("modrinth_gallery", []))
    for name, title, featured in GALLERY:
        if name in done:
            continue
        from urllib.parse import quote
        url = (f"{api}/v2/project/{pid}/gallery?ext=png&featured={'true' if featured else 'false'}"
               f"&title={quote(title)}")
        request("POST", url, auth, (ROOT / "media" / name).read_bytes(), "image/png")
        done.add(name)
        state["modrinth_gallery"] = sorted(done)
        save_state(state)
        print("gallery:", name)

    if submit:
        request("PATCH", f"{api}/v2/project/{pid}", auth, json.dumps({"status": "processing"}).encode(),
                "application/json")
        print("submitted for Modrinth review")
    print(f"https://modrinth.com/mod/{pid}")


def curseforge(project_id):
    import os
    token = os.environ["CURSEFORGE_TOKEN"]
    auth = {"X-Api-Token": token}
    api = "https://minecraft.curseforge.com/api"
    versions = request("GET", f"{api}/game/versions", auth)
    wanted = {("26.3", None), ("Fabric", None), ("Client", None), ("Server", None), ("Java 25", None)}
    ids = []
    for name, _ in wanted:
        match = [v for v in versions if v["name"] == name]
        if name == "26.3":
            # several "26.3" entries exist (Minecraft version types); prefer the one under a "Minecraft 26" type
            types = {t["id"]: t["name"] for t in request("GET", f"{api}/game/version-types", auth)}
            match = [v for v in match if "Minecraft" in types.get(v["gameVersionTypeID"], "")] or match
        if not match:
            sys.exit(f"CurseForge has no game version named {name}")
        ids.append(match[0]["id"])
    metadata = {
        "changelog": CHANGELOG, "changelogType": "markdown", "displayName": f"Lava Genesis {VERSION}",
        "gameVersions": ids, "releaseType": "release",
        "relations": {"projects": [{"slug": "fabric-api", "type": "requiredDependency"}]},
    }
    body, ctype = multipart({"metadata": json.dumps(metadata)},
                            {"file": (JAR.name, JAR.read_bytes(), "application/java-archive")})
    result = request("POST", f"{api}/projects/{project_id}/upload-file", auth, body, ctype)
    state = load_state()
    state["curseforge_project"] = project_id
    state["curseforge_file"] = result["id"]
    save_state(state)
    print("uploaded CurseForge file", result["id"])


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "modrinth":
        modrinth("--submit" in sys.argv)
    elif len(sys.argv) >= 3 and sys.argv[1] == "curseforge":
        curseforge(sys.argv[2])
    else:
        sys.exit(__doc__)
