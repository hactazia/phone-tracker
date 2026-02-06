-- CreateTable
CREATE TABLE "locations" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "uid" TEXT NOT NULL,
    "latitude" REAL NOT NULL,
    "longitude" REAL NOT NULL,
    "accuracy" REAL,
    "requested" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateIndex
CREATE INDEX "locations_uid_requested_idx" ON "locations"("uid", "requested");

-- CreateIndex
CREATE INDEX "locations_requested_idx" ON "locations"("requested");
