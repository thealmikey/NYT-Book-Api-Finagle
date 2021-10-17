# ING-Api-Challenge

## HOW TO RUN

#### _assumptions_
- you have sbt and docker installed on your machine

### Steps

- In the project directory run the following command in the terminal
  `sbt docker:publishLocal` - this will build a docker image that will host our API at port `:8080`

- Once we have the image create, navigate to the `docker` folder in the terminal. There we have
  the `docker-compose.yml` file that we will use to launch our Redis together with our app

The command needed to run it is `docker compose up`

- Once our app is up and running we'll then test our endpoints on `localhost:8080`

Example query GET: `localhost:8080/me/books/list?author=king`

An example result:

```json

{
    "authorSearchTerm": "king",
    "authorWithBooks": [
        {
            "author": "Stephen King and Owen King",
            "books": [
                {
                    "name": "SLEEPING BEAUTIES",
                    "publisher": "Gallery",
                    "publish_date": "2017-10-22"
                }
            ]
        }

...
                        ]
}
```

- We can query by year of publishing `localhost:8080/me/books/list?author=king&year=2014`

The above query would return all authors named **king** who published books in the year **2014**

For every author result, they would be accompanied by a books array if they published multiple books within the years searched.

An example result:

```json
{
    "authorSearchTerm": "king",
    "authorWithBooks": [
        {
            "author": "Ross King",
            "books": [
                {
                    "name": "The Judgment of Paris: The Revolutionary Decade That Gave the World Impressionism",
                    "publisher": null,
                    "publish_date": null
                },
                {
                    "name": "Leonardo and the Last Supper",
                    "publisher": null,
                    "publish_date": null
                },
                {
                    "name": "Ex-Libris",
                    "publisher": null,
                    "publish_date": null
                },
                {
                    "name": "Brunelleschi's Dome: How a Renaissance Genius Reinvented Architecture",
                    "publisher": null,
                    "publish_date": null
                },
                {
                    "name": "Domino",
                    "publisher": null,
                    "publish_date": null
                },
                {
                    "name": "Brunelleschi's Dome: How a Renaissance Genius Reinvented Architecture",
                    "publisher": null,
                    "publish_date": null
                },
                {
                    "name": "Domino",
                    "publisher": null,
                    "publish_date": null
                }
            ]
        }
        ...
    ]
}

```

- Results are cached using `Redis` and accessed using [Redis4Cats]("https://github.com/profunktor/redis4cats) which gives us a nice functional interface and
  polymorphism over the asynchronous effects, allows for smooth composition as we fetch data online, transform it into a result for the API and then cache it


