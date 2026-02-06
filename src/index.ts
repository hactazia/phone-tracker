import express, { Application } from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { MqttService } from './mqtt.service';
import routes from './routes';

// Charger les variables d'environnement
dotenv.config();

const app: Application = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Servir les fichiers statiques
app.use(express.static('public'));

// Routes
app.use('/api', routes);

// Route de santé
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    mqtt: mqttService.getStatus(),
  });
});

// Route racine
app.get('/', (req, res) => {
  res.json({
    message: 'Location Tracker Server',
    version: '1.0.0',
    endpoints: {
      health: '/health',
      locations: '/api/locations',
      latest: '/api/locations/latest',
      range: '/api/locations/range',
      stats: '/api/stats',
    },
  });
});

// Initialiser le service MQTT
const mqttService = new MqttService(
  process.env.MQTT_BROKER_URL || 'ssl://mqtt.mondomaine.fr:8883',
  process.env.MQTT_USERNAME || 'user1',
  process.env.MQTT_PASSWORD || 'password',
);

// Démarrer le serveur
app.listen(PORT, () => {
  console.log(`🚀 Server is running on port ${PORT}`);
  console.log(`📍 Health check: http://localhost:${PORT}/health`);
  console.log(`🌐 API: http://localhost:${PORT}/api`);
  
  // Connexion MQTT
  mqttService.connect();
});

// Gestion de l'arrêt propre
process.on('SIGINT', () => {
  console.log('\n⚠️ Shutting down gracefully...');
  mqttService.disconnect();
  process.exit(0);
});

process.on('SIGTERM', () => {
  console.log('\n⚠️ Shutting down gracefully...');
  mqttService.disconnect();
  process.exit(0);
});

export default app;
