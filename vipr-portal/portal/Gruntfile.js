module.exports = function(grunt) {
    grunt.loadNpmTasks('grunt-ngdocs');
    grunt.loadNpmTasks('grunt-contrib-connect');
    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-contrib-watch');
    grunt.loadNpmTasks('grunt-concurrent');

    grunt.initConfig({
        ngdocs: {
            options: {
                dest: 'docs/build',
                scripts: [
                    "../public/javascripts/jquery-1.11.0.js",
                    "../public/javascripts/chosen.jquery.js",
                    "../public/javascripts/bootstrap.js",
                    "../public/javascripts/common.js",
                    "../public/javascripts/jquery.knob.js",
                    "../public/javascripts/sprintf.js",
                    "../public/lib/momentjs/moment.js",
                    "angular.js",
                    "docs/js/config.js",
                    "../public/javascripts/ng/vipr.js",
                    "../public/javascripts/ng/fields.js",
                    "../public/javascripts/ng/tags.js"
                ],
                styles:[
                    "../public/stylesheets/bootstrap.css",
                    "../public/stylesheets/common.css",
                    "../public/stylesheets/chosen.css",
                    "../public/stylesheets/chosen-custom.css",
                    "../public/stylesheets/vipricons.css"
                ],
                html5Mode: false,
                startPage: '/api',
                title: "ViPR AngularJS Docs",
                titleLink: "/api",
                bestMatch: true
            },
            api: {
                src: [
                    'public/javascripts/ng/*.js',
                    'docs/api/*.ngdoc'
                ],
                title: 'API Documentation'
            },
            tutorial: {
                src: [
                    'docs/tutorial/*.ngdoc',
                    'docs/tutorial/*.js'
                ],
                title: "Tutorial"
            }
        },
        connect: {
            options: {
                keepalive: true,
                base: ['docs/build', '.'],
                port: 2001
            },
            server: {}
        },
        watch: {
            scripts: {
                files: [
                    'public/javascripts/**/*.js',
                    'docs/**/*.ngdoc'
                ],
                tasks: ['clean', 'ngdocs']
            }
        },
        concurrent: {
            target: {
                tasks: ['watch', 'connect'],
                options: {
                    logConcurrentOutput: true
                }
            }
        },

        clean: ['docs/build']
    });

    grunt.registerTask('default', ['clean', 'ngdocs']);
    grunt.registerTask('serve', ['clean', 'ngdocs', 'concurrent']);

};
