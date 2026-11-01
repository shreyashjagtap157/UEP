import { createServer } from 'http';
import { readFile } from 'fs';

const server = createServer((req, res) => {
  readFile('dist/index.html', (err, data) => {
    if (err) {
      res.writeHead(500);
      res.end('Error loading index.html');
      return;
    }
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(data);
  });
});

server.listen(5173, 'localhost', () => {
  console.log('Server running at http://localhost:5173/');
});