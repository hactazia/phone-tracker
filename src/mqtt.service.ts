import * as mqtt from 'mqtt';
import prisma from './lib/prisma';

export interface LocationData {
  pvd: string;  // provider (e.g., "gps", "network")
  lat: number;  // latitude
  lng: number;  // longitude
  tts: number;  // timestamp
  acc?: number; // accuracy (optional)
}

interface LocationPayload {
  latitude: number;
  longitude: number;
  accuracy?: number;
  timestamp: Date;
}

export class MqttService {
  private client: mqtt.MqttClient | null = null;
  private isConnected = false;

  constructor(
    private brokerUrl: string,
    private username: string,
    private password: string
  ) { }

  connect(): void {
    console.log(`Connecting to MQTT broker: ${this.brokerUrl}`);

    this.client = mqtt.connect(this.brokerUrl, {
      username: this.username,
      password: this.password,
      reconnectPeriod: 5000,
      clean: true,
    });

    this.client.on('connect', () => {
      console.log('✅ Connected to MQTT broker');
      this.isConnected = true;
      this.subscribe();
    });

    this.client.on('error', (error) => {
      console.error('❌ MQTT connection error:', error);
      this.isConnected = false;
    });

    this.client.on('close', () => {
      console.log('⚠️ MQTT connection closed');
      this.isConnected = false;
    });

    this.client.on('reconnect', () => {
      console.log('🔄 Reconnecting to MQTT broker...');
    });

    this.client.on('message', (topic, message) => {
      this.handleMessage(topic, message);
    });
  }

  private subscribe(): void {
    if (!this.client || !this.isConnected) {
      console.error('Cannot subscribe: not connected');
      return;
    }

    this.client.subscribe('tracker/#', (err) => {
      if (err) {
        console.error(`Failed to subscribe to tracker/#:`, err);
      } else {
        console.log(`📡 Subscribed to topic: tracker/#`);
      }
    });
  }

  private async handleMessage(topic: string, message: Buffer): Promise<void> {
    try {
      // Log tous les messages reçus pour débugger
      console.log(`🔔 Message received on topic: "${topic}"`);
      console.log(`📦 Raw message: ${message.toString()}`);

      // tracker/{uid}
      const match = topic.match(/^tracker\/(.+)$/);
      if (!match) {
        console.warn(`⚠️ Topic doesn't match pattern: ${topic}`);
        return;
      }

      console.log(`✅ Topic matched! UID extracted: "${match[1]}"`);
      const payload: LocationData = JSON.parse(message.toString());
      console.log(`📍 Parsed location data:`, payload);

      // Convertir le format du téléphone vers notre format DB
      const locationData: LocationPayload = {
        latitude: payload.lat,
        longitude: payload.lng,
        accuracy: payload.acc,
        timestamp: new Date(payload.tts),
      };

      // Sauvegarder dans la base de données
      const location = await prisma.location.create({
        data: {
          uid: match[1],
          latitude: locationData.latitude,
          longitude: locationData.longitude,
          accuracy: locationData.accuracy,
          requested: locationData.timestamp,
        },
      });

      console.log(`✅ Location saved to database with ID: ${location.uid}:${location.requested.getTime()}`);
    } catch (error) {
      console.error('Error processing location message:', error);
    }
  }

  disconnect(): void {
    if (this.client) {
      this.client.end();
      this.isConnected = false;
      console.log('Disconnected from MQTT broker');
    }
  }

  getStatus(): { connected: boolean; topic: string } {
    return {
      connected: this.isConnected,
      topic: 'tracker/#',
    };
  }
}
