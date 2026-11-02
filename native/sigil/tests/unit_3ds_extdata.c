#include "legacy_units.h"

#define ROOT "sdmc/Nintendo 3DS/00000000000000000000000000000000/00000000000000000000000000000000/"
#define TITLE ROOT "title/"
#define EXT ROOT "extdata/00000000/"

static int failures;
#define CHECK(test) do { if (!(test)) { fprintf(stderr, "line %d: %s\n", __LINE__, #test); failures++; } } while (0)

typedef struct {
    sigil_result identity;
    sigil_sync_request request;
    mem_root *root;
} game;

static void text(mem_root *root, const char *path, const char *value) {
    root_put(root, path, (const uint8_t *)value, strlen(value));
}

static bool holds(mem_root *root, const char *path, const char *value) {
    mem_file *file = root_find(root, path);
    return file && file->len == strlen(value) && memcmp(file->data, value, file->len) == 0;
}

static void init(game *g, mem_root *root, const char *id, const char *layout) {
    memset(g, 0, sizeof(*g));
    g->root = root;
    g->identity.struct_version = SIGIL_RESULT_V4;
    g->identity.platform = SIGIL_PLATFORM_3DS;
    snprintf(g->identity.title_id, sizeof(g->identity.title_id), "%s", id);
    snprintf(g->identity.save_id, sizeof(g->identity.save_id), "%s", id);
    g->request.struct_version = SIGIL_SYNC_REQUEST_V1;
    g->request.save.struct_version = SIGIL_SAVE_REQUEST_V1;
    g->request.save.layout = layout;
    g->request.save.platform = "3ds";
    g->request.save.content_path = "game.3ds";
    g->request.save.result = &g->identity;
    g->request.save.listing = root->listing;
    g->request.save.open = root_open;
    g->request.save.open_ctx = root;
    g->request.write = root_write;
    g->request.remove = root_remove;
    g->request.write_ctx = root;
    g->request.overwrite_local = 1;
}

static int collect(game *g, sigil_sync_result **out) {
    g->request.save.listing_count = g->root->count;
    return sigil_collect(&g->request, out);
}

static int restore(game *g, const sigil_sync_result *unit, sigil_sync_result **out) {
    g->request.save.listing_count = g->root->count;
    return sigil_restore(&g->request, unit->data, unit->len, out);
}

static sigil_sync_result unit(const char *const *names, size_t count) {
    sigil_sync_result u = {0};
    uint8_t *values[8];
    size_t lengths[8];
    for (size_t i = 0; i < count; i++) { values[i] = (uint8_t *)"cloud"; lengths[i] = 5; }
    u.data = legacy_zip(names, values, lengths, count, &u.len);
    CHECK(u.data != NULL);
    return u;
}

static void verified_ids(void) {
    const char *ids[] = { "0004000000055E00", "00040000/0011C400", "001B5100", "00040000000FC500", "0004000E/00055E00" };
    const char *exts[] = { "0000055D", "000011C5", "00001B50", "00000FC5", "0000055E" };
    const char *stale[] = { "0000055e", "000011c4", "00001b51", "00000fc6", "0000055d" };
    for (size_t i = 0; i < sizeof(ids) / sizeof(ids[0]); i++) {
        mem_root source = {0}, target = {0};
        char path[1024], old[1024];
        snprintf(path, sizeof(path), EXT "%s/progress", exts[i]);
        snprintf(old, sizeof(old), EXT "%s/stale", stale[i]);
        text(&source, path, "progress");
        text(&source, old, "unrelated");
        game s;
        init(&s, &source, ids[i], "azahar");
        sigil_save_unit *located = NULL;
        s.request.save.listing_count = source.count;
        CHECK(sigil_save_resolve(&s.request.save, &located) == SIGIL_OK);
        CHECK(located && located->member_count == 1);
        sigil_save_unit_free(located);
        sigil_sync_result *found = NULL;
        CHECK(collect(&s, &found) == SIGIL_OK);
        sigil_zip_member *members = NULL;
        size_t n = 0;
        CHECK(found && sigil_zip_read_mem(found->data, found->len, 1024, &members, &n) == SIGIL_OK);
        CHECK(n == 1 && strstr(members[0].name, "/extdata/progress"));
        sigil_zip_members_free(members, n);
        text(&target, path, "older");
        text(&target, old, "unrelated");
        game t;
        init(&t, &target, ids[i], "citra");
        sigil_sync_result *result = NULL;
        if (found) CHECK(restore(&t, found, &result) == SIGIL_OK);
        CHECK(holds(&target, path, "progress"));
        CHECK(holds(&target, old, "unrelated"));
        sigil_sync_result_free(result);
        sigil_sync_result_free(found);
        root_free(&target);
        root_free(&source);
    }
}

static void partial_and_refused(void) {
    mem_root target = {0};
    text(&target, TITLE "00040000/00055e00/data/local", "data");
    text(&target, EXT "0000055D/local", "extdata");
    text(&target, EXT "0000055e/stale", "stale");
    game g;
    init(&g, &target, "00055E00", "lime3ds");
    const char *data_names[] = { "data/new" };
    sigil_sync_result data = unit(data_names, 1), *r = NULL;
    CHECK(restore(&g, &data, &r) == SIGIL_OK);
    CHECK(holds(&target, EXT "0000055D/local", "extdata"));
    CHECK(holds(&target, TITLE "00040000/00055e00/data/new", "cloud"));
    sigil_sync_result_free(r);
    const char *ext_names[] = { "extdata/new" };
    sigil_sync_result ext = unit(ext_names, 1);
    r = NULL;
    CHECK(restore(&g, &ext, &r) == SIGIL_OK);
    CHECK(holds(&target, TITLE "00040000/00055e00/data/new", "cloud"));
    CHECK(holds(&target, EXT "0000055D/new", "cloud"));
    CHECK(!root_find(&target, EXT "0000055D/local"));
    CHECK(holds(&target, EXT "0000055e/stale", "stale"));
    sigil_sync_result_free(r);

    const char *bad_names[][2] = {
        { "data/new", "elsewhere/new" },
        { "data/new", "extdata/../../escape" },
        { "data/new", "data/new" },
        { "data/new", "data/new/child" },
    };
    for (size_t i = 0; i < 4; i++) {
        sigil_sync_result bad = unit(bad_names[i], 2);
        int writes = target.writes, removes = target.removes;
        uint8_t *before = malloc(bad.len);
        memcpy(before, bad.data, bad.len);
        r = NULL;
        CHECK(restore(&g, &bad, &r) != SIGIL_OK);
        CHECK(target.writes == writes && target.removes == removes);
        CHECK(memcmp(before, bad.data, bad.len) == 0);
        free(before);
        free(bad.data);
        sigil_sync_result_free(r);
    }
    size_t payload_at = 30 + strlen(data_names[0]);
    data.data[payload_at] ^= 1;
    int crc_writes = target.writes, crc_removes = target.removes;
    r = NULL;
    CHECK(restore(&g, &data, &r) == SIGIL_ERR_UNSUPPORTED_FORMAT);
    CHECK(target.writes == crc_writes && target.removes == crc_removes);
    sigil_sync_result_free(r);
    data.data[payload_at] ^= 1;

    sigil_sync_result broken = { .data = (uint8_t *)"not a zip", .len = 9 };
    int writes = target.writes, removes = target.removes;
    r = NULL;
    CHECK(restore(&g, &broken, &r) != SIGIL_OK);
    CHECK(target.writes == writes && target.removes == removes);
    sigil_sync_result_free(r);

    const char *replacement_names[] = { "extdata/replacement" };
    sigil_sync_result replacement = unit(replacement_names, 1);
    target.fail_write = target.writes + 1;
    r = NULL;
    CHECK(restore(&g, &replacement, &r) == SIGIL_ERR_IO);
    CHECK(target.removes == removes);
    CHECK(holds(&target, EXT "0000055D/new", "cloud"));
    CHECK(holds(&target, TITLE "00040000/00055e00/data/new", "cloud"));
    sigil_sync_result_free(r);
    free(replacement.data);
    target.fail_write = 0;
    writes = target.writes;

    text(&target, EXT "0000055d/other-case", "ambiguous");
    r = NULL;
    CHECK(restore(&g, &data, &r) == SIGIL_ERR_AMBIGUOUS);
    CHECK(target.writes == writes && target.removes == removes);
    sigil_sync_result_free(r);
    free(data.data);
    free(ext.data);
    root_free(&target);
}

static void known_none(void) {
    const char *ids[] = { "000400000008D300", "0008D300", "00040000/0008d300" };
    for (size_t i = 0; i < 3; i++) {
        mem_root target = {0};
        text(&target, TITLE "00040000/0008d300/data/local", "data");
        text(&target, EXT "000008d3/local", "stale");
        game g;
        init(&g, &target, ids[i], "azahar");
        const char *names[] = { "data/new", "extdata/new" };
        sigil_sync_result bad = unit(names, 2), *r = NULL;
        CHECK(restore(&g, &bad, &r) == SIGIL_ERR_NO_TARGET);
        CHECK(target.writes == 0 && target.removes == 0);
        CHECK(holds(&target, TITLE "00040000/0008d300/data/local", "data"));
        sigil_sync_result_free(r);
        r = NULL;
        CHECK(collect(&g, &r) == SIGIL_OK);
        sigil_zip_member *members = NULL;
        size_t n = 0;
        CHECK(r && sigil_zip_read_mem(r->data, r->len, 1024, &members, &n) == SIGIL_OK);
        CHECK(n == 1 && strstr(members[0].name, "/data/local"));
        sigil_zip_members_free(members, n);
        sigil_sync_result_free(r);
        free(bad.data);
        root_free(&target);
    }
}

static void malformed_ids(void) {
    const char *ids[] = { "00055E0", "00040000/00055e00/extra", "00040000/not-hex!" };
    for (size_t i = 0; i < 3; i++) {
        mem_root root = {0};
        game g;
        init(&g, &root, ids[i], "azahar");
        const char *names[] = { "data/new" };
        sigil_sync_result incoming = unit(names, 1), *r = NULL;
        CHECK(restore(&g, &incoming, &r) == SIGIL_ERR_INVALID_ARG);
        CHECK(root.writes == 0 && root.removes == 0);
        sigil_sync_result_free(r);
        free(incoming.data);
        root_free(&root);
    }
}

static void empty_directory_hints(void) {
    const char *ids[] = { "0000055d", "0000055D" };
    for (size_t i = 0; i < 2; i++) {
        mem_root root = {0};
        char directory[1024], path[1024];
        snprintf(directory, sizeof(directory), EXT "%s/", ids[i]);
        snprintf(path, sizeof(path), EXT "%s/main", ids[i]);
        text(&root, directory, "");
        game g;
        init(&g, &root, "00055E00", "azahar");
        sigil_sync_result *r = NULL;
        CHECK(collect(&g, &r) == SIGIL_OK);
        CHECK(r && !r->data && !r->len);
        sigil_sync_result_free(r);
        const char *names[] = { "extdata/main" };
        sigil_sync_result incoming = unit(names, 1);
        r = NULL;
        CHECK(restore(&g, &incoming, &r) == SIGIL_OK);
        CHECK(holds(&root, path, "cloud"));
        CHECK(root.count == 2);
        sigil_sync_result_free(r);
        free(incoming.data);
        root_free(&root);
    }
    mem_root collision = {0};
    text(&collision, EXT "0000055d/", "");
    text(&collision, EXT "0000055D/", "");
    game g;
    init(&g, &collision, "00055E00", "azahar");
    sigil_sync_result *r = NULL;
    CHECK(collect(&g, &r) == SIGIL_ERR_AMBIGUOUS);
    CHECK(collision.writes == 0 && collision.removes == 0);
    sigil_sync_result_free(r);
    root_free(&collision);
}

int main(void) {
    empty_directory_hints();
    malformed_ids();
    verified_ids();
    partial_and_refused();
    known_none();
    return failures ? 1 : 0;
}
