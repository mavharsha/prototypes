const { faker } = require('@faker-js/faker');
const fs = require('fs');
const path = require('path');

// Directory for persistent data
const DATA_DIR = '/data';
const LOG_FILE = path.join(DATA_DIR, 'events.log');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// Function to generate a fake event
function generateEvent() {
  const event = {
    timestamp: new Date().toISOString(),
    eventId: faker.string.uuid(),
    eventType: faker.helpers.arrayElement(['user_login', 'user_logout', 'purchase', 'page_view', 'api_call']),
    user: {
      id: faker.string.uuid(),
      name: faker.person.fullName(),
      email: faker.internet.email(),
      ip: faker.internet.ip()
    },
    metadata: {
      userAgent: faker.internet.userAgent(),
      location: `${faker.location.city()}, ${faker.location.country()}`,
      device: faker.helpers.arrayElement(['mobile', 'desktop', 'tablet']),
      amount: faker.number.float({ min: 10, max: 1000, precision: 0.01 })
    }
  };
  
  return event;
}

// Function to write event to file
function writeEvent() {
  const event = generateEvent();
  const eventLine = JSON.stringify(event) + '\n';
  
  // Append to file
  fs.appendFile(LOG_FILE, eventLine, (err) => {
    if (err) {
      console.error('Error writing event:', err);
    } else {
      console.log(`[${event.timestamp}] Event logged: ${event.eventType} - ${event.user.name}`);
    }
  });
}

// Initial log
console.log('========================================');
console.log('Docker Volume Event Logger Started');
console.log('========================================');
console.log(`Logging events to: ${LOG_FILE}`);
console.log('Writing events every 15 seconds...\n');

// Write first event immediately
writeEvent();

// Write events every 15 seconds
setInterval(writeEvent, 15000);

// Handle graceful shutdown
process.on('SIGTERM', () => {
  console.log('\n========================================');
  console.log('Shutting down gracefully...');
  console.log('========================================');
  process.exit(0);
});

process.on('SIGINT', () => {
  console.log('\n========================================');
  console.log('Shutting down gracefully...');
  console.log('========================================');
  process.exit(0);
});

