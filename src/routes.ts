import { Router, Request, Response } from 'express';
import prisma from './lib/prisma';
import { authMiddleware } from './middleware/auth';

const router = Router();

// Appliquer le middleware d'authentification à toutes les routes
router.use(authMiddleware);

// GET /api/locations - Récupérer toutes les localisations
router.get('/locations', async (req: Request, res: Response) => {
  try {
    const { userId = 'user1', limit = '50', offset = '0' } = req.query;

    const locations = await prisma.location.findMany({
      where: {
        uid: userId as string,
      },
      orderBy: {
        requested: 'desc',
      },
      take: parseInt(limit as string),
      skip: parseInt(offset as string),
    });

    const total = await prisma.location.count({
      where: {
        uid: userId as string,
      },
    });

    res.json({
      success: true,
      data: locations,
      pagination: {
        total,
        limit: parseInt(limit as string),
        offset: parseInt(offset as string),
      },
    });
  } catch (error) {
    console.error('Error fetching locations:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch locations',
    });
  }
});

// GET /api/locations/latest - Récupérer la dernière localisation
router.get('/locations/latest', async (req: Request, res: Response) => {
  try {
    const { userId = 'user1' } = req.query;

    const location = await prisma.location.findFirst({
      where: {
        uid: userId as string,
      },
      orderBy: {
        requested: 'desc',
      },
    });

    if (!location) {
      return res.status(404).json({
        success: false,
        error: 'No location found',
      });
    }

    res.json({
      success: true,
      data: location,
    });
  } catch (error) {
    console.error('Error fetching latest location:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch latest location',
    });
  }
});

// GET /api/locations/range - Récupérer les localisations dans une plage de dates
router.get('/locations/range', async (req: Request, res: Response) => {
  try {
    const { userId = 'user1', from, to } = req.query;

    if (!from || !to) {
      return res.status(400).json({
        success: false,
        error: 'Both "from" and "to" parameters are required',
      });
    }

    const locations = await prisma.location.findMany({
      where: {
        uid: userId as string,
        requested: {
          gte: new Date(from as string),
          lte: new Date(to as string),
        },
      },
      orderBy: {
        requested: 'desc',
      },
    });

    res.json({
      success: true,
      data: locations,
      count: locations.length,
    });
  } catch (error) {
    console.error('Error fetching locations by range:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch locations',
    });
  }
});

// GET /api/locations/filtered - Récupérer les localisations avec filtres after/before
router.get('/locations/filtered', async (req: Request, res: Response) => {
  try {
    const { uid, after, before } = req.query;

    if (!uid) {
      return res.status(400).json({
        success: false,
        error: 'Parameter "uid" is required',
      });
    }

    // Construire les conditions de filtre
    const whereConditions: any = {
      uid: uid as string,
    };

    // Ajouter les filtres de date si présents
    if (after || before) {
      whereConditions.requested = {};

      if (after) {
        whereConditions.requested.gte = new Date(after as string);
      }

      if (before) {
        whereConditions.requested.lte = new Date(before as string);
      }
    }

    const locations = await prisma.location.findMany({
      where: whereConditions,
      orderBy: {
        requested: 'asc',
      },
    });

    res.json({
      success: true,
      data: locations,
      count: locations.length,
    });
  } catch (error) {
    console.error('Error fetching filtered locations:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch locations',
    });
  }
});

// DELETE /api/locations - Supprimer toutes les localisations anciennes
router.delete('/locations', async (req: Request, res: Response) => {
  try {
    const { userId = 'user1', beforeDate } = req.query;

    if (!beforeDate) {
      return res.status(400).json({
        success: false,
        error: 'Parameter "beforeDate" is required',
      });
    }

    const result = await prisma.location.deleteMany({
      where: {
        uid: userId as string,
        requested: {
          lt: new Date(beforeDate as string),
        },
      },
    });

    res.json({
      success: true,
      deleted: result.count,
    });
  } catch (error) {
    console.error('Error deleting locations:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to delete locations',
    });
  }
});

// GET /api/stats - Statistiques
router.get('/stats', async (req: Request, res: Response) => {
  try {
    const { userId = 'user1' } = req.query;

    const total = await prisma.location.count({
      where: { uid: userId as string },
    });

    const oldest = await prisma.location.findFirst({
      where: { uid: userId as string },
      orderBy: { requested: 'asc' },
    });

    const latest = await prisma.location.findFirst({
      where: { uid: userId as string },
      orderBy: { requested: 'desc' },
    });

    res.json({
      success: true,
      data: {
        totalLocations: total,
        oldestLocation: oldest?.requested || null,
        latestLocation: latest?.requested || null,
      },
    });
  } catch (error) {
    console.error('Error fetching stats:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch stats',
    });
  }
});

// GET /api/users - Récupérer la liste des UIDs disponibles
router.get('/users', async (req: Request, res: Response) => {
  try {
    const users = await prisma.location.findMany({
      distinct: ['uid'],
      select: {
        uid: true,
      },
      orderBy: {
        uid: 'asc',
      },
    });

    const uids = users.map(u => u.uid);

    res.json({
      success: true,
      data: uids,
      count: uids.length,
    });
  } catch (error) {
    console.error('Error fetching users:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch users',
    });
  }
});

export default router;
